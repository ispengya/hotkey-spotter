package com.ispengya.hkcache.server;

import com.ispengya.hkcache.remoting.server.NettyServer;
import com.ispengya.hkcache.server.scheduler.HotKeyScheduler;

/**
 * HotKeyServerBootstrap 服务端启动引导类。
 *
 * <p>负责协调 Netty 服务和聚合调度器的生命周期管理。</p>
 *
 * @author ispengya
 */
public final class HotKeyServerBootstrap {

    private final NettyServer nettyServer;
    private final HotKeyScheduler hotKeyScheduler;

    /**
     * 构造启动引导类。
     *
     * @param nettyServer        Netty 服务端
     * @param hotKeyScheduler 聚合调度器
     */
    public HotKeyServerBootstrap(NettyServer nettyServer,
                                 HotKeyScheduler hotKeyScheduler) {
        this.nettyServer = nettyServer;
        this.hotKeyScheduler = hotKeyScheduler;
    }

    /**
     * 启动服务。
     */
    public void start() {
        nettyServer.start();
        hotKeyScheduler.start();
    }

    /**
     * 停止服务。
     */
    public void stop() {
        hotKeyScheduler.stop();
        nettyServer.stop();
    }
}
