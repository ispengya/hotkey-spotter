package com.ispengya.hotkey.server;

import com.ispengya.hotkey.remoting.server.NettyServer;
import com.ispengya.hotkey.server.nacos.NacosIntegration;
import com.ispengya.hotkey.server.scheduler.HotKeyScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HotKeyServerBootstrap 服务端启动引导类。
 *
 * <p>负责协调 Netty 服务、聚合调度器和 Nacos 集成的生命周期管理。</p>
 *
 * @author ispengya
 */
public final class HotKeyServerBootstrap {

    private static final Logger log = LoggerFactory.getLogger(HotKeyServerBootstrap.class);

    private final NettyServer nettyServer;
    private final HotKeyScheduler hotKeyScheduler;
    private final NacosIntegration nacosIntegration;

    /**
     * 构造启动引导类。
     *
     * @param nettyServer      Netty 服务端
     * @param hotKeyScheduler  聚合调度器
     * @param nacosIntegration Nacos 集成
     */
    public HotKeyServerBootstrap(NettyServer nettyServer,
                                 HotKeyScheduler hotKeyScheduler,
                                 NacosIntegration nacosIntegration) {
        this.nettyServer = nettyServer;
        this.hotKeyScheduler = hotKeyScheduler;
        this.nacosIntegration = nacosIntegration;
    }

    /**
     * 启动服务。
     */
    public void start() {
        log.info("Starting HotKeyServer...");
        nacosIntegration.init();
        nettyServer.start();
        hotKeyScheduler.start();
        log.info("HotKeyServer started.");
    }

    /**
     * 停止服务。
     */
    public void stop() {
        log.info("Stopping HotKeyServer...");
        hotKeyScheduler.stop();
        nettyServer.stop();
        nacosIntegration.shutdown();
        log.info("HotKeyServer stopped.");
    }
}
