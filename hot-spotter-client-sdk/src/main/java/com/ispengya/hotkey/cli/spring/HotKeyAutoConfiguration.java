package com.ispengya.hotkey.cli.spring;

import cn.hutool.core.collection.CollUtil;
import com.ispengya.hotkey.cli.cache.CacheHolder;
import com.ispengya.hotkey.cli.config.HotKeyProperties;
import com.ispengya.hotkey.cli.HotKeyClient;
import com.ispengya.hotkey.cli.detect.HotKeyReporter;
import com.ispengya.hotkey.cli.nacos.NacosRegistry;
import com.ispengya.hotkey.cli.nacos.WorkerDiscovery;
import com.ispengya.hotkey.remoting.client.ClientRequestSender;
import com.ispengya.hotkey.remoting.client.HotKeyRemotingClient;
import com.ispengya.hotkey.remoting.client.NettyClient;
import com.ispengya.hotkey.remoting.client.NettyClientConfig;
import com.ispengya.hotkey.remoting.protocol.Fastjson2Serializer;
import com.ispengya.hotkey.remoting.protocol.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HotKey 自动配置类。
 *
 * <p>负责初始化 HotKey 客户端的所有组件：
 * <ul>
 *     <li>注册中心：NacosRegistry（可选）</li>
 *     <li>通信层：NettyClient、HotKeyRemotingClient</li>
 *     <li>核心业务：HotKeyReporter、HotKeyClient</li>
 * </ul>
 * </p>
 *
 * @author ispengya
 */
@Configuration
@ConditionalOnClass(HotKeyClient.class)
@EnableConfigurationProperties(HotKeyProperties.class)
public class HotKeyAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(HotKeyAutoConfiguration.class);

    // ==================== 注册中心 ====================

    /**
     * 注册中心（可选，当 nacos 启用时创建）。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "hotkey.nacos", name = "enabled", havingValue = "true")
    public NacosRegistry hotkeyNacosRegistry(HotKeyProperties properties) {
        NacosRegistry registry = new NacosRegistry(properties);
        registry.init();
        return registry;
    }

    // ==================== 通信层 ====================

    @Bean
    @ConditionalOnMissingBean
    public Serializer hotkeySerializer() {
        return new Fastjson2Serializer();
    }

    @Bean
    @ConditionalOnMissingBean
    public NettyClient hotkeyNettyClient(HotKeyProperties properties,
                                          @Autowired(required = false) NacosRegistry nacosRegistry) {
        HotKeyProperties.ClientConfig clientConfig = properties.getClient();
        List<InetSocketAddress> serverAddresses;

        // 优先使用 nacos 服务发现
        WorkerDiscovery workerDiscovery = nacosRegistry != null
                ? nacosRegistry.getWorkerDiscovery()
                : null;

        if (workerDiscovery != null) {
            serverAddresses = workerDiscovery.getWorkerAddresses();
            if (serverAddresses.isEmpty()) {
                log.warn("No workers found from nacos, falling back to static addresses");
                serverAddresses = buildServerAddresses(clientConfig);
            }
            // 监听 worker 列表变更
            workerDiscovery.setAddressChangeListener(addresses -> {
                log.info("Worker list changed, reconnecting: {}", addresses);
                // TODO: 实现 NettyClient 重连逻辑
            });
        } else {
            serverAddresses = buildServerAddresses(clientConfig);
        }

        int connectTimeoutMillis = clientConfig.getConnectTimeoutMillis();
        int workerThreads = clientConfig.getWorkerThreads();
        int maxFrameBytes = clientConfig.getMaxFrameBytes();
        int pushPoolSize = clientConfig.getPushPoolSize();
        int reportPoolSize = clientConfig.getReportPoolSize();
        String appName = properties.getAppName();
        NettyClientConfig config = new NettyClientConfig(
                serverAddresses,
                connectTimeoutMillis,
                workerThreads,
                maxFrameBytes,
                pushPoolSize,
                reportPoolSize,
                appName
        );
        NettyClient client = new NettyClient(config);
        client.start();
        return client;
    }

    @Bean
    @ConditionalOnMissingBean
    public HotKeyRemotingClient hotkeyRemotingClient(HotKeyProperties properties,
                                                      Serializer serializer,
                                                      NettyClient nettyClient) {
        ClientRequestSender sender = new ClientRequestSender(nettyClient);
        String appName = properties.getAppName();
        HotKeyRemotingClient client = new HotKeyRemotingClient(serializer, sender, appName);
        client.registerPushChannel();
        return client;
    }

    // ==================== 核心业务 ====================

    @Bean
    @ConditionalOnMissingBean
    public HotKeyReporter hotkeyHotKeyReporter(HotKeyProperties properties,
                                                HotKeyRemotingClient remotingClient) {
        HotKeyProperties.DetectConfig detectConfig = properties.getDetect();
        long reportPeriodMillis = detectConfig.getReportPeriodMillis();
        long queryPeriodMillis = detectConfig.getQueryPeriodMillis();
        long queryTimeoutMillis = detectConfig.getQueryTimeoutMillis();
        String appName = properties.getAppName();
        HotKeyReporter reporter = new HotKeyReporter(
                remotingClient,
                appName,
                reportPeriodMillis,
                queryPeriodMillis,
                queryTimeoutMillis
        );
        reporter.start();
        log.info("HotKeyReporter started: appName={}, reportPeriodMillis={}", appName, reportPeriodMillis);
        return reporter;
    }

    @Bean
    @ConditionalOnMissingBean
    public HotKeyClient hotkeyHotKeyClient(HotKeyReporter reporter,
                                            HotKeyProperties properties) {
        String appName = properties.getAppName();
        CacheHolder cacheHolder = new CacheHolder(appName);
        HotKeyClient client = new HotKeyClient(reporter, cacheHolder);

        // 设置热 key 监听器，将推送事件转发给 HotKeyClient
        reporter.setHotKeyListener(new HotKeyReporter.HotKeyListener() {
            @Override
            public void onHotKeyAdded(String key, int durationSeconds) {
                client.addHotKey(key, durationSeconds);
            }

            @Override
            public void onHotKeyRemoved(String key) {
                client.removeHotKey(key);
            }
        });

        log.info("HotKeyClient created: appName={}", appName);
        return client;
    }

    // ==================== 工具方法 ====================

    private List<InetSocketAddress> buildServerAddresses(HotKeyProperties.ClientConfig clientConfig) {
        List<String> addresses = clientConfig.getServerAddresses();
        if (CollUtil.isEmpty(addresses)) {
            return Collections.singletonList(new InetSocketAddress("127.0.0.1", 8888));
        }
        List<InetSocketAddress> result = new ArrayList<>();
        for (String addr : addresses) {
            if (addr == null || addr.isEmpty()) {
                continue;
            }
            String[] parts = addr.split(":");
            if (parts.length != 2) {
                continue;
            }
            String host = parts[0].trim();
            String portPart = parts[1].trim();
            if (host.isEmpty()) {
                continue;
            }
            try {
                int port = Integer.parseInt(portPart);
                result.add(new InetSocketAddress(host, port));
            } catch (NumberFormatException ignored) {
            }
        }
        if (result.isEmpty()) {
            return Collections.singletonList(new InetSocketAddress("127.0.0.1", 8888));
        }
        return result;
    }
}
