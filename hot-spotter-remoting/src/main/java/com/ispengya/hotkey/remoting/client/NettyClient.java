package com.ispengya.hotkey.remoting.client;

import com.ispengya.hotkey.common.event.EventBus;
import com.ispengya.hotkey.remoting.codec.CommandDecoder;
import com.ispengya.hotkey.common.event.WorkerChangeEvent;
import com.ispengya.hotkey.remoting.codec.CommandEncoder;
import com.ispengya.hotkey.remoting.protocol.Command;
import com.ispengya.hotkey.remoting.protocol.CommandType;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * NettyClient 封装基于 Netty 的客户端连接管理。
 *
 * <p>参考 hotkey 项目设计：
 * <ul>
 *     <li>每个 Worker 一个 Channel，双向通信</li>
 *     <li>按 key hash 选择 Channel，保证同一 key 总是发到同一 Worker</li>
 *     <li>支持动态增减 Worker</li>
 * </ul>
 * </p>
 *
 * @author ispengya
 */
public final class NettyClient {

    private static final Logger log = LoggerFactory.getLogger(NettyClient.class);

    /**
     * 客户端配置。
     */
    private final NettyClientConfig config;

    /**
     * 事件总线。
     */
    private final EventBus eventBus;

    /**
     * worker 事件循环组。
     */
    private EventLoopGroup workerGroup;

    /**
     * Netty Bootstrap 实例。
     */
    private Bootstrap bootstrap;

    /**
     * Worker 列表（有序，用于 hash 路由）。
     */
    private final CopyOnWriteArrayList<WorkerInfo> workers = new CopyOnWriteArrayList<>();

    public NettyClient(NettyClientConfig config, EventBus eventBus) {
        this.config = config;
        this.eventBus = eventBus;
    }

    /**
     * 初始化 Netty 客户端。
     */
    public void start() {
        // 监听 Worker 地址变更
        eventBus.register(WorkerChangeEvent.class, event -> {
            log.info("Worker change detected: {}", event.getWorkers());
            mergeAndConnect(event.getWorkers());
        });

        workerGroup = new NioEventLoopGroup(config.getWorkerThreads());
        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
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

        // 连接初始 Worker
        List<InetSocketAddress> addresses = config.getServerAddresses();
        if (addresses != null && !addresses.isEmpty()) {
            connect(addresses);
        }

        // 心跳
        workerGroup.next().scheduleAtFixedRate(this::heartbeat, 30, 30, TimeUnit.SECONDS);

        log.info("NettyClient started, workers={}", workers);
    }

    /**
     * 根据 key 选择 Channel（hash 路由）。
     *
     * @param key 业务 key
     * @return Channel，如果没有可用 Worker 返回 null
     */
    public Channel chooseChannel(String key) {
        int size = workers.size();
        if (size == 0) {
            return null;
        }
        int index = Math.abs(key.hashCode() % size);
        WorkerInfo worker = workers.get(index);
        return worker.channel;
    }

    /**
     * 获取任意一个可用 Channel。
     *
     * @return Channel，如果没有可用 Worker 返回 null
     */
    public Channel pickChannel() {
        for (WorkerInfo worker : workers) {
            if (worker.channel != null && worker.channel.isActive()) {
                return worker.channel;
            }
        }
        return null;
    }

    /**
     * 连接到新的 Worker 地址列表。
     *
     * <p>合并新旧地址：移除不再需要的，连接新增的。</p>
     *
     * @param newAddresses 新的 Worker 地址列表
     */
    public synchronized void mergeAndConnect(List<InetSocketAddress> newAddresses) {
        // 1. 移除不再需要的 Worker
        Set<String> newAddressSet = new HashSet<>();
        for (InetSocketAddress addr : newAddresses) {
            newAddressSet.add(addr.getHostString() + ":" + addr.getPort());
        }

        Iterator<WorkerInfo> it = workers.iterator();
        while (it.hasNext()) {
            WorkerInfo worker = it.next();
            String addrStr = worker.address.getHostString() + ":" + worker.address.getPort();
            if (!newAddressSet.contains(addrStr)) {
                log.info("Removing worker: {}", addrStr);
                if (worker.channel != null) {
                    worker.channel.close();
                }
                workers.remove(worker);
            }
        }

        // 2. 连接新增的 Worker
        Set<String> existingSet = new HashSet<>();
        for (WorkerInfo worker : workers) {
            existingSet.add(worker.address.getHostString() + ":" + worker.address.getPort());
        }

        for (InetSocketAddress addr : newAddresses) {
            String addrStr = addr.getHostString() + ":" + addr.getPort();
            if (!existingSet.contains(addrStr)) {
                connectOne(addr);
            }
        }

        // 3. 排序（保证 hash 路由一致性）
        workers.sort(Comparator.comparing(w -> w.address.getHostString() + ":" + w.address.getPort()));

        log.info("Workers after merge: {}", workers);
    }

    /**
     * 连接到指定地址列表（初始连接）。
     */
    private void connect(List<InetSocketAddress> addresses) {
        for (InetSocketAddress address : addresses) {
            connectOne(address);
        }
        workers.sort(Comparator.comparing(w -> w.address.getHostString() + ":" + w.address.getPort()));
    }

    /**
     * 连接单个 Worker。
     */
    private void connectOne(InetSocketAddress address) {
        try {
            ChannelFuture future = bootstrap.connect(address).sync();
            Channel channel = future.channel();
            WorkerInfo worker = new WorkerInfo(address, channel);
            workers.add(worker);
            log.info("Connected to worker: {}", address);
        } catch (Exception e) {
            log.error("Failed to connect to worker: {}", address, e);
            // 连接失败也记录，后续重试
            workers.add(new WorkerInfo(address, null));
        }
    }

    /**
     * 处理 Channel 断开事件。
     *
     * @param channel 断开的 Channel
     */
    public void onChannelInactive(Channel channel) {
        for (WorkerInfo worker : workers) {
            if (worker.channel == channel) {
                log.info("Worker disconnected: {}", worker.address);
                workers.remove(worker);
                break;
            }
        }
    }

    /**
     * 心跳。
     */
    private void heartbeat() {
        for (WorkerInfo worker : workers) {
            try {
                if (worker.channel != null && worker.channel.isActive()) {
                    Command ping = new Command(CommandType.ADMIN_PING, 0L, null);
                    worker.channel.writeAndFlush(ping);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 停止客户端。
     */
    public void stop() {
        for (WorkerInfo worker : workers) {
            if (worker.channel != null) {
                worker.channel.close();
            }
        }
        workers.clear();
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("NettyClient stopped");
    }

    /**
     * Worker 信息。
     */
    private static class WorkerInfo {
        final InetSocketAddress address;
        volatile Channel channel;

        WorkerInfo(InetSocketAddress address, Channel channel) {
            this.address = address;
            this.channel = channel;
        }

        @Override
        public String toString() {
            return address.getHostString() + ":" + address.getPort();
        }
    }
}
