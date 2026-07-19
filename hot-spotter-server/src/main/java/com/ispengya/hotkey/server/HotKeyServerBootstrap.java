package com.ispengya.hotkey.server;

import com.ispengya.hotkey.remoting.server.NettyServer;
import com.ispengya.hotkey.server.nacos.NacosIntegration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HotKeyServerBootstrap 服务端启动引导类。
 *
 * <p>负责协调 Netty 服务和 Nacos 集成的生命周期管理。</p>
 *
 * @author ispengya
 */
public final class HotKeyServerBootstrap {

    private static final Logger log = LoggerFactory.getLogger(HotKeyServerBootstrap.class);

    private final NettyServer nettyServer;
    private final NacosIntegration nacosIntegration;

    public HotKeyServerBootstrap(NettyServer nettyServer,
                                 NacosIntegration nacosIntegration) {
        this.nettyServer = nettyServer;
        this.nacosIntegration = nacosIntegration;
    }

    public void start() {
        log.info("Starting HotKeyServer...");
        nacosIntegration.init();
        nettyServer.start();
        log.info("HotKeyServer started.");
    }

    public void stop() {
        log.info("Stopping HotKeyServer...");
        nettyServer.stop();
        nacosIntegration.shutdown();
        log.info("HotKeyServer stopped.");
    }
}
