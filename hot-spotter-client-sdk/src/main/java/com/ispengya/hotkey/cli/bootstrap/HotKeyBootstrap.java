package com.ispengya.hotkey.cli.bootstrap;

import com.ispengya.hotkey.cli.HotKeyClient;
import com.ispengya.hotkey.cli.config.HotKeyProperties;
import com.ispengya.hotkey.cli.detect.HotKeyReporter;
import com.ispengya.hotkey.cli.event.EventBus;
import com.ispengya.hotkey.cli.event.HotKeyEvent;
import com.ispengya.hotkey.cli.event.RuleChangeEvent;
import com.ispengya.hotkey.cli.event.WorkerChangeEvent;
import com.ispengya.hotkey.cli.nacos.NacosRegistry;
import com.ispengya.hotkey.cli.rule.RuleHolder;
import com.ispengya.hotkey.remoting.client.HotKeyRemotingClient;
import com.ispengya.hotkey.remoting.client.NettyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HotKey 启动引导类，统一管理所有组件的生命周期。
 *
 * @author ispengya
 */
public final class HotKeyBootstrap {

    private static final Logger log = LoggerFactory.getLogger(HotKeyBootstrap.class);

    private final String appName;
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
        this.appName = properties.getAppName();
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

        // 2. 注册 EventBus 事件监听
        registerEventListeners();

        // 3. Netty 启动
        nettyClient.start();

        // 4. 注册推送通道
        remotingClient.registerPushChannel();

        // 5. 启动上报器
        reporter.start();

        log.info("HotKey client started successfully");
    }

    /**
     * 注册 EventBus 事件监听。
     */
    private void registerEventListeners() {
        eventBus.register(WorkerChangeEvent.class, event -> {
            nettyClient.onWorkerChange(event.getWorkers());
        });

        eventBus.register(RuleChangeEvent.class, event -> {
            RuleHolder.updateRuleConfig(appName, event.getRuleConfig());
            log.info("Rule config updated: {}", event.getRuleConfig());
        });

        remotingClient.setPushCallback(message -> {
            eventBus.post(new HotKeyEvent(message));
        });
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
