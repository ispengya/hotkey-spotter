package com.ispengya.hotkey.remoting.client;

import com.ispengya.hotkey.remoting.protocol.Command;
import io.netty.channel.Channel;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ClientRequestSender 封装客户端请求发送逻辑。
 *
 * <p>对上层暴露简化的 One-Way 和 Sync 两种发送方式。</p>
 */
public final class ClientRequestSender {

    /**
     * 底层 NettyClient，用于建立连接。
     */
    private final NettyClient nettyClient;

    private static final AtomicLong REQUEST_ID = new AtomicLong(0L);

    /**
     * 构造请求发送器。
     *
     * @param nettyClient Netty 客户端
     */
    public ClientRequestSender(NettyClient nettyClient) {
        this.nettyClient = nettyClient;
    }

    /**
     * 发送单向请求，不关心响应结果。
     *
     * @param command 待发送的命令
     */
    public void sendOneWay(Command command) {
        try {
            Channel channel = nettyClient.pickReportChannel();
            channel.writeAndFlush(command);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void sendOneWayOnPushChannel(Command command) {
        try {
            Channel channel = nettyClient.pickPushChannel();
            channel.writeAndFlush(command);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 发送同步请求，返回一个可等待的 Future。
     *
     * @param command      待发送的命令
     * @param timeoutMillis 超时时间（上层负责处理超时）
     * @return 包含响应 Command 的 CompletableFuture
     */
    public CompletableFuture<Command> sendSync(Command command, long timeoutMillis) {
        CompletableFuture<Command> future = new CompletableFuture<>();
        try {
            long requestId = REQUEST_ID.incrementAndGet();
            Command cmdWithId = new Command(command.getType(), requestId, command.getPayload());
            ClientInboundHandler.registerFuture(requestId, future);
            Channel channel = nettyClient.pickReportChannel();
            channel.writeAndFlush(cmdWithId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.completeExceptionally(e);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }
}
