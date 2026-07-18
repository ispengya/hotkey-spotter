package com.ispengya.hotkey.cli.bootstrap;

import com.ispengya.hotkey.cli.HotKeyClient;
import com.ispengya.hotkey.cli.config.HotKeyProperties;
import com.ispengya.hotkey.cli.detect.HotKeyReporter;
import com.ispengya.hotkey.cli.nacos.NacosRegistry;
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

    private final EventBus eventBus;
    private final NacosRegistry nacosRegistry;
    private final NettyClient nettyClient;
    private final HotKeyRemotingClient remotingClient;
    private final HotKeyReporter reporter;

    public HotKeyBootstrap(HotKeyProperties properties,
                           EventBus eventBus,
                           NacosRegistry nacosRegistry,
                           NettyClient nettyClient,
                           HotKeyRemotingClient remotingClient,
                           HotKeyReporter reporter,
                           HotKeyClient hotKeyClient) {
        this.eventBus = eventBus;
        this.nacosRegistry = nacosRegistry;
        this.nettyClient = nettyClient;
        this.remotingClient = remotingClient;
        this.reporter = reporter;
    }

    /**
     * 启动所有组件。
     */
    public void start() {
        log.info("Starting HotKey client...");

        // 1. 初始化 Nacos
        nacosRegistry.init();
        nacosRegistry.afterPropertiesSet();

        // 2. 启动 Netty（会自动监听 WorkerChangeEvent）
        nettyClient.start();

        // 3. 注册推送通道
        remotingClient.registerPushChannel();

        // 4. 启动上报器（会自动监听 HotKeyPushEvent）
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
        nacosRegistry.shutdown();
        eventBus.clear();

        log.info("HotKey client shutdown complete");
    }
}
