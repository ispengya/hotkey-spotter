package com.ispengya.hkcache.remoting.client;

import com.ispengya.hkcache.remoting.codec.CommandDecoder;
import com.ispengya.hkcache.remoting.codec.CommandEncoder;
import com.ispengya.hkcache.remoting.protocol.Command;
import com.ispengya.hkcache.remoting.protocol.CommandType;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NettyClient 封装基于 Netty 的客户端启动与连接逻辑。
 *
 * <p>客户端采用短连接模式：每次请求创建一次连接，用完即关。</p>
 */
public final class NettyClient {

    /**
     * 客户端配置。
     */
    private final NettyClientConfig config;

    /**
     * worker 事件循环组。
     */
    private EventLoopGroup workerGroup;

    /**
     * Netty Bootstrap 实例。
     */
    private Bootstrap bootstrap;

    private final int pushPoolSize;

    private final int reportPoolSize;

    private final List<Channel> pushChannelPool;

    private final List<Channel> reportChannelPool;

    private final AtomicInteger nextPushIndex = new AtomicInteger(0);

    private final AtomicInteger nextReportIndex = new AtomicInteger(0);

    /**
     * 绑定的服务端地址（首次连接时选定，用于保持绑定语义）。
     */
    private volatile InetSocketAddress boundAddress;

    public NettyClient(NettyClientConfig config) {
        this.config = config;
        this.pushPoolSize = Math.max(1, config.getPushPoolSize());
        this.reportPoolSize = Math.max(1, config.getReportPoolSize());
        this.pushChannelPool = new ArrayList<>(pushPoolSize);
        this.reportChannelPool = new ArrayList<>(reportPoolSize);
    }

    /**
     * 初始化 Netty 客户端，包括 EventLoopGroup 和 ChannelPipeline。
     */
    public void start() {
        workerGroup = new NioEventLoopGroup(config.getWorkerThreads());
        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeoutMillis())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new CommandDecoder(config.getMaxFrameLength()));
                        p.addLast(new CommandEncoder());
                        p.addLast(new ClientInboundHandler());
                    }
                });
        workerGroup.next().scheduleAtFixedRate(() -> {
            try {
                Channel channel = pickPushChannel();
                if (channel != null && channel.isActive()) {
                    Command ping = new Command(CommandType.ADMIN_PING, 0L, null);
                    channel.writeAndFlush(ping);
                }
            } catch (Exception ignored) {
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * 获取或建立连接池中的一个可用 Channel（用于 one-way 或异步请求）。
     *
     * @return 可用的 Channel
     * @throws InterruptedException 连接被中断时抛出
     */
    public Channel getOrCreateChannel() throws InterruptedException {
        return pickReportChannel();
    }

    public Channel pickReportChannel() throws InterruptedException {
        ensureReportPoolInitialized();
        int start = Math.abs(nextReportIndex.getAndIncrement());
        for (int i = 0; i < reportPoolSize; i++) {
            int idx = (start + i) % reportPoolSize;
            Channel ch = reportChannelPool.get(idx);
            if (ch != null && ch.isActive()) {
                return ch;
            }
        }
        ensureReportPoolInitialized();
        return reportChannelPool.get(0);
    }

    public Channel pickPushChannel() throws InterruptedException {
        ensurePushPoolInitialized();
        int start = Math.abs(nextPushIndex.getAndIncrement());
        for (int i = 0; i < pushPoolSize; i++) {
            int idx = (start + i) % pushPoolSize;
            Channel ch = pushChannelPool.get(idx);
            if (ch != null && ch.isActive()) {
                return ch;
            }
        }
        ensurePushPoolInitialized();
        return pushChannelPool.get(0);
    }

    public Channel pickChannel() throws InterruptedException {
        return pickReportChannel();
    }

    private InetSocketAddress selectServerAddress() {
        List<InetSocketAddress> addresses = config.getServerAddresses();
        if (addresses == null || addresses.isEmpty()) {
            throw new IllegalStateException("No server addresses configured");
        }
        if (addresses.size() == 1) {
            return addresses.get(0);
        }
        String appName = config.getAppName();
        if (appName == null || appName.isEmpty()) {
            return addresses.get(0);
        }
        int idx = Math.abs(appName.hashCode()) % addresses.size();
        return addresses.get(idx);
    }

    private void ensurePushPoolInitialized() throws InterruptedException {
        synchronized (this) {
            if (boundAddress == null) {
                boundAddress = selectServerAddress();
            }
            while (pushChannelPool.size() < pushPoolSize) {
                pushChannelPool.add(null);
            }
            for (int i = 0; i < pushPoolSize; i++) {
                Channel ch = pushChannelPool.get(i);
                if (ch == null || !ch.isActive()) {
                    ChannelFuture future = bootstrap.connect(boundAddress).sync();
                    pushChannelPool.set(i, future.channel());
                }
            }
        }
    }

    private void ensureReportPoolInitialized() throws InterruptedException {
        synchronized (this) {
            if (boundAddress == null) {
                boundAddress = selectServerAddress();
            }
            while (reportChannelPool.size() < reportPoolSize) {
                reportChannelPool.add(null);
            }
            for (int i = 0; i < reportPoolSize; i++) {
                Channel ch = reportChannelPool.get(i);
                if (ch == null || !ch.isActive()) {
                    ChannelFuture future = bootstrap.connect(boundAddress).sync();
                    reportChannelPool.set(i, future.channel());
                }
            }
        }
    }

    /**
     * 停止客户端并释放资源。
     */
    public void stop() {
        for (Channel ch : pushChannelPool) {
            if (ch != null) {
                ch.close();
            }
        }
        for (Channel ch : reportChannelPool) {
            if (ch != null) {
                ch.close();
            }
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }
}
