package com.ispengya.hotkey.cli.bootstrap;

import com.ispengya.hotkey.cli.HotKeyClient;
import com.ispengya.hotkey.cli.config.HotKeyProperties;
import com.ispengya.hotkey.cli.detect.HotKeyReporter;
import com.ispengya.hotkey.cli.listener.HotKeyPushListener;
import com.ispengya.hotkey.cli.listener.RuleChangeListener;
import com.ispengya.hotkey.cli.listener.WorkerChangeListener;
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
    private final HotKeyClient hotKeyClient;

    public HotKeyBootstrap(HotKeyProperties properties,
                           EventBus eventBus,
                           NacosBridge nacosBridge,
                           NettyClient nettyClient,
                           HotKeyRemotingClient remotingClient,
                           HotKeyReporter reporter,
                           HotKeyClient hotKeyClient) {
        this.properties = properties;
        this.eventBus = eventBus;
        this.nacosBridge = nacosBridge;
        this.nettyClient = nettyClient;
        this.remotingClient = remotingClient;
        this.reporter = reporter;
        this.hotKeyClient = hotKeyClient;
    }

    /**
     * 启动所有组件。
     */
    public void start() {
        log.info("Starting HotKey client...");

        // 1. 初始化 Nacos 连接（创建 ConfigService / NamingService）
        nacosBridge.init();

        // 2. 注册规则变更监听器（必须在 syncRules 之前，确保首次拉取规则时也能被捕获）
        new RuleChangeListener(eventBus);

        // 3. 启动 Netty
        nettyClient.start();

        // 4. 注册 Worker 变更监听器（必须在 startWorkerDiscovery 之前）
        new WorkerChangeListener(eventBus, nettyClient);

        // 5. 注册热 Key 推送监听器（必须在 startWorkerDiscovery 之前）
        new HotKeyPushListener(eventBus, hotKeyClient);

        // 6. 同步规则配置
        HotKeyProperties.NacosConfig nacos = properties.getNacos();
        nacosBridge.syncRules(nacos.getDataId(), nacos.getGroup(), properties.getAppName());

        // 7. 启动 Worker 发现 → 发布 WorkerChangeEvent → NettyClient 收到后懒建连
        nacosBridge.startWorkerDiscovery(nacos.getServiceName());

        // 8. 启动上报器
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
