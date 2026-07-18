package com.ispengya.hotkey.cli.bootstrap;

import com.ispengya.hotkey.cli.config.HotKeyProperties;
import com.ispengya.hotkey.cli.detect.HotKeyReporter;
import com.ispengya.hotkey.cli.nacos.NacosBridge;
import com.ispengya.hotkey.common.event.EventBus;
import com.ispengya.hotkey.remoting.client.HotKeyRemotingClient;
import com.ispengya.hotkey.remoting.client.NettyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HotKey 启动引导类，统一管理所有组件的生命周期。
 *
 * <p>各组件自己监听 EventBus，Bootstrap 只负责启动顺序。</p>
 *
 * @author ispengya
 */
public final class HotKeyBootstrap {

    private static final Logger log = LoggerFactory.getLogger(HotKeyBootstrap.class);

    private final HotKeyProperties properties;
    private final EventBus eventBus;
    private final NacosBridge nacosBridge;
    private final NettyClient nettyClient;
    private final HotKeyRemotingClient remotingClient;
    private final HotKeyReporter reporter;

    public HotKeyBootstrap(HotKeyProperties properties,
                           EventBus eventBus,
                           NacosBridge nacosBridge,
                           NettyClient nettyClient,
                           HotKeyRemotingClient remotingClient,
                           HotKeyReporter reporter) {
        this.properties = properties;
        this.eventBus = eventBus;
        this.nacosBridge = nacosBridge;
        this.nettyClient = nettyClient;
        this.remotingClient = remotingClient;
        this.reporter = reporter;
    }

    /**
     * 启动所有组件。
     */
    public void start() {
        log.info("Starting HotKey client...");

        // 1. 初始化 Nacos 连接
        nacosBridge.init();

        // 2. 同步规则配置
        HotKeyProperties.NacosConfig nacos = properties.getNacos();
        nacosBridge.syncRules(nacos.getDataId(), nacos.getGroup(), properties.getAppName());

        // 3. 启动 Worker 发现（首次拉取 + 订阅 + 定时兜底）
        nacosBridge.startWorkerDiscovery(nacos.getServiceName());

        // 4. 启动 Netty（监听 WorkerChangeEvent，连接初始 Worker）
        nettyClient.start();

        // 5. 注册推送通道
        remotingClient.registerPushChannel();

        // 6. 启动上报器（监听 HotKeyPushEvent）
        reporter.start();

        log.info("HotKey client started successfully");
    }

    /**
     * 关闭所有组件。
     */
    public void shutdown() {
        log.info("Shutting down HotKey client...");

        reporter.stop();
        nettyClient.stop();
        nacosBridge.shutdown();
        eventBus.clear();

        log.info("HotKey client shutdown complete");
    }
}
