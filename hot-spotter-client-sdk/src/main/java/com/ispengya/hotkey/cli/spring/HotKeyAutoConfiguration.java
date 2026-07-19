package com.ispengya.hotkey.cli.spring;

import com.ispengya.hotkey.cli.HotKeyClient;
import com.ispengya.hotkey.cli.bootstrap.HotKeyBootstrap;
import com.ispengya.hotkey.cli.cache.CacheHolder;
import com.ispengya.hotkey.cli.config.HotKeyProperties;
import com.ispengya.hotkey.cli.detect.HotKeyReporter;
import com.ispengya.hotkey.cli.nacos.NacosBridge;
import com.ispengya.hotkey.common.event.EventBus;
import com.ispengya.hotkey.remoting.client.ClientRequestSender;
import com.ispengya.hotkey.remoting.client.HotKeyRemotingClient;
import com.ispengya.hotkey.remoting.client.NettyClient;
import com.ispengya.hotkey.remoting.protocol.Fastjson2Serializer;
import com.ispengya.hotkey.remoting.protocol.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Configuration;

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

    // ==================== Nacos 桥接 ====================

    @Bean
    @ConditionalOnMissingBean
    public NacosBridge hotkeyNacosBridge(HotKeyProperties properties, EventBus eventBus) {
        HotKeyProperties.NacosConfig nacos = properties.getNacos();

        // group 不配时自动使用 appName，保证 Nacos 三层结构：
        // namespace(环境) / group(服务) / dataId(配置类型)
        if (nacos.getGroup() == null || StringUtils.isBlank(nacos.getGroup())) {
            nacos.setGroup(properties.getAppName());
        }

        return new NacosBridge(nacos.getServerAddr(), nacos.getNamespace(), eventBus);
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
                                          EventBus eventBus,
                                          Serializer serializer) {
        HotKeyProperties.ClientConfig clientConfig = properties.getClient();
        return new NettyClient(
                clientConfig.getConnectTimeoutMillis(),
                clientConfig.getWorkerThreads(),
                clientConfig.getMaxFrameBytes(),
                eventBus,
                serializer
        );
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
                                                HotKeyRemotingClient remotingClient) {
        HotKeyProperties.DetectConfig detectConfig = properties.getDetect();
        return new HotKeyReporter(
                remotingClient,
                properties.getAppName(),
                detectConfig.getReportPeriodMillis()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public HotKeyClient hotkeyHotKeyClient(HotKeyReporter reporter,
                                            HotKeyProperties properties) {
        CacheHolder cacheHolder = new CacheHolder(properties.getAppName());
        return new HotKeyClient(reporter, cacheHolder);
    }

    // ==================== 启动器 ====================

    @Bean
    @ConditionalOnMissingBean
    public HotKeyBootstrap hotkeyBootstrap(HotKeyProperties properties,
                                            EventBus eventBus,
                                            NacosBridge nacosBridge,
                                            NettyClient nettyClient,
                                            HotKeyRemotingClient remotingClient,
                                            HotKeyReporter reporter,
                                            HotKeyClient hotKeyClient) {
        HotKeyBootstrap bootstrap = new HotKeyBootstrap(
                properties,
                eventBus,
                nacosBridge,
                nettyClient,
                remotingClient,
                reporter,
                hotKeyClient
        );
        bootstrap.start();
        return bootstrap;
    }
}
