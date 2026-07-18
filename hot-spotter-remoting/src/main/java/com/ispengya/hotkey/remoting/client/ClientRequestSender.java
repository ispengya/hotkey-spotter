package com.ispengya.hotkey.remoting.client;

import com.ispengya.hotkey.remoting.protocol.Command;
import io.netty.channel.Channel;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ClientRequestSender 封装客户端请求发送逻辑。
 *
 * <p>对上层暴露简化的 One-Way 和 Sync 两种发送方式。</p>
 *
 * @author ispengya
 */
public final class ClientRequestSender {

    private final NettyClient nettyClient;

    private static final AtomicLong REQUEST_ID = new AtomicLong(0L);

    public ClientRequestSender(NettyClient nettyClient) {
        this.nettyClient = nettyClient;
    }

    /**
     * 发送单向请求（上报访问数据）。
     *
     * <p>按 appName hash 选择 Worker，保证同一应用的数据发到同一 Worker。</p>
     *
     * @param command 待发送的命令
     * @param key     用于路由的 key
     */
    public void sendOneWay(Command command, String key) {
        Channel channel = nettyClient.chooseChannel(key);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(command);
        }
    }

    /**
     * 发送单向请求到任意可用 Worker。
     *
     * @param command 待发送的命令
     */
    public void sendOneWay(Command command) {
        Channel channel = nettyClient.pickChannel();
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(command);
        }
    }

    /**
     * 发送同步请求，返回一个可等待的 Future。
     *
     * @param command       待发送的命令
     * @param timeoutMillis 超时时间
     * @return 包含响应 Command 的 CompletableFuture
     */
    public CompletableFuture<Command> sendSync(Command command, long timeoutMillis) {
        CompletableFuture<Command> future = new CompletableFuture<>();
        try {
            long requestId = REQUEST_ID.incrementAndGet();
            Command cmdWithId = new Command(command.getType(), requestId, command.getPayload());
            ClientInboundHandler.registerFuture(requestId, future);
            Channel channel = nettyClient.pickChannel();
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(cmdWithId);
            } else {
                future.completeExceptionally(new IllegalStateException("No available worker"));
            }
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }
}
