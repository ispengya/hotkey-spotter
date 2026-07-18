package com.ispengya.hotkey.remoting.client;

import com.ispengya.hotkey.common.event.EventBus;
import com.ispengya.hotkey.common.event.WorkerChangeEvent;
import com.ispengya.hotkey.remoting.codec.CommandDecoder;
import com.ispengya.hotkey.remoting.codec.CommandEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Netty 客户端，管理与 Worker 的长连接。
 *
 * <p>核心设计：
 * <ul>
 *     <li><b>懒建连</b>：只有对某台 Worker 有数据要发时才建立连接，
 *         而不是启动时连上所有 Worker</li>
 *     <li><b>Hash 路由</b>：同一 key 在所有客户端上 hash 到同一台 Worker</li>
 *     <li><b>Worker 变更</b>：监听 WorkerChangeEvent，更新地址列表，
 *         断开已下线的 Worker Channel，新 Worker 懒建连</li>
 * </ul>
 * </p>
 *
 * @author ispengya
 */
public final class NettyClient {

    private static final Logger log = LoggerFactory.getLogger(NettyClient.class);

    private final EventBus eventBus;
    private final int connectTimeoutMillis;
    private final int workerThreads;
    private final int maxFrameLength;

    private EventLoopGroup workerGroup;
    private Bootstrap bootstrap;

    /**
     * 排序后的 Worker 地址列表（保证 hash 路由一致性），
     * 由 WorkerChangeEvent 更新。
     */
    private volatile List<InetSocketAddress> sortedWorkers = Collections.emptyList();

    /**
     * 懒建连 Channel 缓存：地址 → Channel。
     */
    private final ConcurrentHashMap<InetSocketAddress, Channel> channelMap = new ConcurrentHashMap<>();

    public NettyClient(int connectTimeoutMillis, int workerThreads, int maxFrameLength, EventBus eventBus) {
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.workerThreads = workerThreads;
        this.maxFrameLength = maxFrameLength;
        this.eventBus = eventBus;
    }

    /**
     * 启动：注册 WorkerChangeEvent 监听，初始化 Bootstrap。
     */
    public void start() {
        eventBus.register(WorkerChangeEvent.class, event -> {
            onWorkerChange(event.getWorkers());
        });

        workerGroup = new NioEventLoopGroup(workerThreads);
        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new CommandDecoder(maxFrameLength));
                        p.addLast(new CommandEncoder());
                        p.addLast(new ClientInboundHandler());
                    }
                });

        log.info("NettyClient started");
    }

    // ==================== 路由选择 ====================

    /**
     * 根据 key hash 选择对应的 Worker Channel，不存在则懒建连。
     *
     * @param key 业务 key
     * @return Channel，无可用 Worker 时返回 null
     */
    public Channel chooseChannel(String key) {
        List<InetSocketAddress> workers = this.sortedWorkers;
        if (workers.isEmpty()) {
            return null;
        }
        int index = Math.abs(key.hashCode() % workers.size());
        return getOrConnect(workers.get(index));
    }

    /**
     * 获取任意一个可用 Channel（用于不关心路由的操作：推送通道注册、查询等）。
     *
     * @return Channel，无可用 Worker 时返回 null
     */
    public Channel pickChannel() {
        // 优先复用已有连接
        for (Map.Entry<InetSocketAddress, Channel> entry : channelMap.entrySet()) {
            Channel ch = entry.getValue();
            if (ch != null && ch.isActive()) {
                return ch;
            }
        }
        // 没有已有连接，懒连第一台
        List<InetSocketAddress> workers = this.sortedWorkers;
        if (workers.isEmpty()) {
            return null;
        }
        return getOrConnect(workers.get(0));
    }

    // ==================== 连接管理（内部） ====================

    /**
     * 获取或懒建连到指定 Worker 的 Channel。
     */
    private Channel getOrConnect(InetSocketAddress address) {
        Channel channel = channelMap.get(address);
        if (channel != null && channel.isActive()) {
            return channel;
        }

        synchronized (this) {
            channel = channelMap.get(address);
            if (channel != null && channel.isActive()) {
                return channel;
            }

            try {
                ChannelFuture future = bootstrap.connect(address).sync();
                Channel ch = future.channel();
                ch.closeFuture().addListener(f -> {
                    channelMap.remove(address, ch);
                    log.info("Worker disconnected: {}", address);
                });
                channelMap.put(address, ch);
                log.info("Connected to worker: {}", address);
                return ch;
            } catch (Exception e) {
                log.error("Failed to connect to worker: {}", address, e);
                return null;
            }
        }
    }

    /**
     * Worker 列表变更：排序、更新列表、关闭已下线 Worker 的 Channel。
     */
    private void onWorkerChange(List<InetSocketAddress> newAddresses) {
        // 排序保证全网 hash 一致
        newAddresses.sort(Comparator.comparing(a -> a.getHostString() + ":" + a.getPort()));
        this.sortedWorkers = new CopyOnWriteArrayList<>(newAddresses);

        // 关闭已下线 Worker 的 Channel
        Set<InetSocketAddress> newSet = new HashSet<>(newAddresses);
        Iterator<Map.Entry<InetSocketAddress, Channel>> it = channelMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<InetSocketAddress, Channel> entry = it.next();
            if (!newSet.contains(entry.getKey())) {
                Channel ch = entry.getValue();
                if (ch != null) {
                    ch.close();
                }
                it.remove();
                log.info("Removed worker channel: {}", entry.getKey());
            }
        }

        log.info("Workers updated: {}", newAddresses);
    }

    // ==================== 关闭 ====================

    public void stop() {
        for (Map.Entry<InetSocketAddress, Channel> entry : channelMap.entrySet()) {
            Channel ch = entry.getValue();
            if (ch != null) {
                ch.close();
            }
        }
        channelMap.clear();
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("NettyClient stopped");
    }
}
