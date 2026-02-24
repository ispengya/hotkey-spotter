package com.ispengya.hotkey.remoting.client;

import com.ispengya.hotkey.remoting.protocol.Command;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ClientInboundHandler extends SimpleChannelInboundHandler<Command> {

    private static final Map<Long, CompletableFuture<Command>> FUTURES = new ConcurrentHashMap<>();

    private static volatile Consumer<Command> pushHandler;

    public static void setPushHandler(Consumer<Command> handler) {
        pushHandler = handler;
    }

    public static void registerFuture(long requestId, CompletableFuture<Command> future) {
        if (requestId != 0L && future != null) {
            FUTURES.put(requestId, future);
        }
    }

    public static void removeFuture(long requestId) {
        if (requestId != 0L) {
            FUTURES.remove(requestId);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Command msg) {
        long requestId = msg.getRequestId();
        if (requestId != 0L) {
            CompletableFuture<Command> future = FUTURES.remove(requestId);
            if (future != null) {
                future.complete(msg);
                return;
            }
        }
        Consumer<Command> handler = pushHandler;
        if (handler != null) {
            handler.accept(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
