package com.ispengya.hotkey.cli.spring;

import cn.hutool.core.collection.CollUtil;
import com.ispengya.hotkey.common.nacos.NacosConfigClient;
import com.ispengya.hotkey.common.rule.RuleConfig;
import com.ispengya.hotkey.cli.rule.RuleHolder;
import com.ispengya.hotkey.remoting.protocol.Fastjson2Serializer;
import com.ispengya.hotkey.remoting.client.ClientRequestSender;
import com.ispengya.hotkey.remoting.client.HotKeyRemotingClient;
import com.ispengya.hotkey.remoting.client.NettyClient;
import com.ispengya.hotkey.remoting.client.NettyClientConfig;
import com.ispengya.hotkey.remoting.client.ServiceDiscoveryClient;
import com.ispengya.hotkey.remoting.protocol.Serializer;
import com.ispengya.hotkey.cli.config.HotKeyProperties;
import com.ispengya.hotkey.cli.core.HotKeyClient;
import com.ispengya.hotkey.cli.detect.HotKeyDetector;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.TimeUnit;

/**
 * HotKeyAutoConfiguration 提供 hotkey 在 Spring Boot 环境下的自动装配，
 * 在检测到 HotKeyClient 存在且配置开启时，自动注册相关 Bean。
 *
 * @author ispengya
 */
@Configuration
@ConditionalOnClass(HotKeyClient.class)
@EnableConfigurationProperties(HotKeyProperties.class)
public class HotKeyAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(HotKeyAutoConfiguration.class);

    /**
     * 注册 HotKeyClientManager，用于集中管理多实例客户端。
     */
    @Bean
    @ConditionalOnMissingBean
    @Deprecated
    public HotKeyClientManager hotKeyClientManager() {
        return new HotKeyClientManager();
    }

    /**
     * 注册 NacosConfigClient（当 nacos 启用时）。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "hotkey.nacos", name = "enabled", havingValue = "true")
    public NacosConfigClient hotkeyNacosConfigClient(HotKeyProperties properties) {
        HotKeyProperties.NacosConfig nacosConfig = properties.getNacos();
        NacosConfigClient client = new NacosConfigClient(
                nacosConfig.getServerAddr(),
                nacosConfig.getNamespace()
        );
        try {
            client.init();
            log.info("NacosConfigClient initialized: serverAddr={}, namespace={}",
                    nacosConfig.getServerAddr(), nacosConfig.getNamespace());
        } catch (Exception e) {
            log.error("Failed to initialize NacosConfigClient", e);
            throw new RuntimeException("Failed to initialize NacosConfigClient", e);
        }

        // 添加规则变更监听器，同步更新 RuleHolder
        client.addRuleChangeListener(new NacosConfigClient.RuleChangeListener() {
            @Override
            public void onRuleChange(String appName, RuleConfig ruleConfig) {
                RuleHolder.updateRuleConfig(appName, ruleConfig);
                log.info("RuleHolder updated for app: {}", appName);
            }
        });

        // 加载已有的规则配置
        String appName = properties.getAppName();
        if (appName != null && !appName.isEmpty()) {
            RuleConfig existingConfig = client.getCachedRuleConfig(appName);
            if (existingConfig != null) {
                RuleHolder.updateRuleConfig(appName, existingConfig);
                log.info("Loaded existing rule config for app: {}", appName);
            }
        }

        return client;
    }

    /**
     * 注册 ServiceDiscoveryClient（当 nacos 启用时）。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "hotkey.nacos", name = "enabled", havingValue = "true")
    public ServiceDiscoveryClient hotkeyServiceDiscoveryClient(NacosConfigClient nacosConfigClient,
                                                                HotKeyProperties properties) {
        HotKeyProperties.NacosConfig nacosConfig = properties.getNacos();
        ServiceDiscoveryClient client = new ServiceDiscoveryClient(
                nacosConfigClient,
                nacosConfig.getServiceName()
        );
        client.init();
        log.info("ServiceDiscoveryClient initialized: serviceName={}", nacosConfig.getServiceName());
        return client;
    }

    @Bean
    @ConditionalOnMissingBean
    public Serializer hotkeySerializer() {
        return new Fastjson2Serializer();
    }

    @Bean
    @ConditionalOnMissingBean
    public NettyClient hotkeyNettyClient(HotKeyProperties properties,
                                          @org.springframework.beans.factory.annotation.Autowired(required = false) ServiceDiscoveryClient serviceDiscoveryClient) {
        HotKeyProperties.ClientConfig clientConfig = properties.getClient();
        List<InetSocketAddress> serverAddresses;

        // 优先使用 nacos 服务发现
        if (serviceDiscoveryClient != null) {
            serverAddresses = serviceDiscoveryClient.getWorkerAddresses();
            if (serverAddresses.isEmpty()) {
                log.warn("No workers found from nacos, falling back to static addresses");
                serverAddresses = buildServerAddresses(clientConfig);
            }
            // 监听 worker 列表变更
            serviceDiscoveryClient.setAddressChangeListener(addresses -> {
                log.info("Worker list changed, reconnecting: {}", addresses);
                // 这里需要重新创建 NettyClient 或者实现 reconnect 方法
                // 暂时简化处理
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

    @Bean
    @ConditionalOnMissingBean
    public HotKeyDetector hotkeyHotKeyDetector(HotKeyProperties properties,
                                                HotKeyRemotingClient remotingClient) {
        HotKeyProperties.DetectConfig detectConfig = properties.getDetect();
        long reportPeriodMillis = detectConfig.getReportPeriodMillis();
        long queryPeriodMillis = detectConfig.getQueryPeriodMillis();
        long queryTimeoutMillis = detectConfig.getQueryTimeoutMillis();
        String appName = properties.getAppName();
        HotKeyDetector detector = new HotKeyDetector(
                remotingClient,
                appName,
                reportPeriodMillis,
                queryPeriodMillis,
                queryTimeoutMillis
        );
        detector.start();
        return detector;
    }

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

    /**
     * 注册默认的 HotKeyClient。
     */
    @Bean(name = HotKeyBeanNames.DEFAULT_CLIENT)
    @ConditionalOnMissingBean
    public HotKeyClient hotkeyHotKeyClient(HotKeyDetector detector,
                                            HotKeyProperties properties) {
        HotKeyClientFactory factory = new HotKeyClientFactory();
        String appName = properties.getAppName();
        return factory.createHotKeyClient(detector, appName);
    }

    private Class<?> resolveClass(String className, Class<?> defaultClass, Class<?> expectedType) {
        if (StringUtils.isEmpty(className)) {
            return defaultClass;
        }
        try {
            Class<?> clazz = Class.forName(className);
            if (!expectedType.isAssignableFrom(clazz)) {
                return defaultClass;
            }
            return clazz;
        } catch (ClassNotFoundException e) {
            return defaultClass;
        }
    }
}
