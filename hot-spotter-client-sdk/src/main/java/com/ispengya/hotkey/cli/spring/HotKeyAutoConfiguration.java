package com.ispengya.hotkey.cli.spring;

import com.ispengya.hotkey.cli.HotKeyClient;
import com.ispengya.hotkey.cli.bootstrap.HotKeyBootstrap;
import com.ispengya.hotkey.cli.cache.CacheHolder;
import com.ispengya.hotkey.cli.config.HotKeyProperties;
import com.ispengya.hotkey.cli.detect.HotKeyReporter;
import com.ispengya.hotkey.cli.nacos.NacosRegistry;
import com.ispengya.hotkey.cli.nacos.WorkerDiscovery;
import com.ispengya.hotkey.common.event.EventBus;
import com.ispengya.hotkey.remoting.client.ClientRequestSender;
import com.ispengya.hotkey.remoting.client.HotKeyRemotingClient;
import com.ispengya.hotkey.remoting.client.NettyClient;
import com.ispengya.hotkey.remoting.client.NettyClientConfig;
import com.ispengya.hotkey.remoting.protocol.Fastjson2Serializer;
import com.ispengya.hotkey.remoting.protocol.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * HotKey 自动配置类。
 *
 * @author ispengya
 */
@Configuration
@ConditionalOnClass(HotKeyClient.class)
@EnableConfigurationProperties(HotKeyProperties.class)
public class HotKeyAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(HotKeyAutoConfiguration.class);

    // ==================== 事件总线 ====================

    @Bean
    @ConditionalOnMissingBean
    public EventBus hotkeyEventBus() {
        return new EventBus();
    }

    // ==================== 注册中心 ====================

    @Bean
    @ConditionalOnMissingBean
    public NacosRegistry hotkeyNacosRegistry(HotKeyProperties properties, EventBus eventBus) {
        return new NacosRegistry(properties, eventBus);
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
                                          NacosRegistry nacosRegistry,
                                          EventBus eventBus) {
        WorkerDiscovery workerDiscovery = nacosRegistry.getWorkerDiscovery();
        List<InetSocketAddress> serverAddresses = workerDiscovery.getWorkerAddresses();

        if (serverAddresses.isEmpty()) {
            throw new IllegalStateException("No workers found from nacos");
        }

        HotKeyProperties.ClientConfig clientConfig = properties.getClient();
        NettyClientConfig config = new NettyClientConfig(
                serverAddresses,
                clientConfig.getConnectTimeoutMillis(),
                clientConfig.getWorkerThreads(),
                clientConfig.getMaxFrameBytes(),
                properties.getAppName()
        );
        return new NettyClient(config, eventBus);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientRequestSender hotkeyClientRequestSender(NettyClient nettyClient) {
        return new ClientRequestSender(nettyClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public HotKeyRemotingClient hotkeyRemotingClient(Serializer serializer,
                                                      ClientRequestSender sender,
                                                      HotKeyProperties properties) {
        return new HotKeyRemotingClient(serializer, sender, properties.getAppName());
    }

    // ==================== 核心业务 ====================

    @Bean
    @ConditionalOnMissingBean
    public HotKeyReporter hotkeyHotKeyReporter(HotKeyProperties properties,
                                                HotKeyRemotingClient remotingClient,
                                                EventBus eventBus) {
        HotKeyProperties.DetectConfig detectConfig = properties.getDetect();
        return new HotKeyReporter(
                remotingClient,
                properties.getAppName(),
                detectConfig.getReportPeriodMillis(),
                detectConfig.getQueryPeriodMillis(),
                detectConfig.getQueryTimeoutMillis(),
                eventBus
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public HotKeyClient hotkeyHotKeyClient(HotKeyReporter reporter,
                                            HotKeyProperties properties) {
        String appName = properties.getAppName();
        CacheHolder cacheHolder = new CacheHolder(appName);
        HotKeyClient client = new HotKeyClient(reporter, cacheHolder);

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

        return client;
    }

    // ==================== 启动器 ====================

    @Bean
    @ConditionalOnMissingBean
    public HotKeyBootstrap hotkeyBootstrap(HotKeyProperties properties,
                                            EventBus eventBus,
                                            NacosRegistry nacosRegistry,
                                            NettyClient nettyClient,
                                            HotKeyRemotingClient remotingClient,
                                            HotKeyReporter reporter,
                                            HotKeyClient hotKeyClient) {
        HotKeyBootstrap bootstrap = new HotKeyBootstrap(
                properties,
                eventBus,
                nacosRegistry,
                nettyClient,
                remotingClient,
                reporter,
                hotKeyClient
        );
        bootstrap.start();
        return bootstrap;
    }
}
