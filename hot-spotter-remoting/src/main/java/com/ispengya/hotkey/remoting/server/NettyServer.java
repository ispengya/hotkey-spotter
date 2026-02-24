package com.ispengya.hotkey.remoting.server;

import com.ispengya.hotkey.remoting.codec.CommandDecoder;
import com.ispengya.hotkey.remoting.codec.CommandEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * NettyServer 封装基于 Netty 的服务端启动与关闭流程。
 *
 * <p>负责创建 EventLoopGroup、ServerBootstrap，并在 ChannelPipeline 中
 * 挂载协议编解码器和业务处理器。</p>
 */
public final class NettyServer {

    /**
     * Netty 服务端配置。
     */
    private final NettyServerConfig config;

    /**
     * 连接管理器，负责管理所有接入的客户端连接。
     */
    private final ServerChannelManager channelManager;

    /**
     * 请求分发器，将 Command 分发到具体业务处理逻辑。
     */
    private final ServerRequestDispatcher dispatcher;

    /**
     * boss 事件循环组。
     */
    private EventLoopGroup bossGroup;

    /**
     * worker 事件循环组。
     */
    private EventLoopGroup workerGroup;

    /**
     * 服务端监听 Channel。
     */
    private Channel serverChannel;

    /**
     * 构造 NettyServer。
     */
    public NettyServer(NettyServerConfig config,
                       ServerChannelManager channelManager,
                       ServerRequestDispatcher dispatcher) {
        this.config = config;
        this.channelManager = channelManager;
        this.dispatcher = dispatcher;
    }

    /**
     * 启动 Netty 服务端，绑定端口并初始化 pipeline。
     */
    public void start() {
        bossGroup = new NioEventLoopGroup(config.getBossThreads());
        workerGroup = new NioEventLoopGroup(config.getWorkerThreads());

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, config.getSoBacklog())
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new IdleStateHandler(
                                config.getReadIdleSeconds(),
                                config.getWriteIdleSeconds(),
                                config.getAllIdleSeconds()
                        ));
                        p.addLast(new CommandDecoder(config.getMaxFrameLength()));
                        p.addLast(new CommandEncoder());
                        p.addLast(new ServerHandler(channelManager, dispatcher));
                    }
                });

        try {
            ChannelFuture bindFuture = bootstrap.bind(config.getPort()).sync();
            serverChannel = bindFuture.channel();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Netty server start failed", e);
        }
    }

    /**
     * 停止 Netty 服务端并释放相关资源。
     */
    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }
}
