package com.ispengya.hotkey.remoting.client;

import com.ispengya.hotkey.common.event.EventBus;
import com.ispengya.hotkey.remoting.event.HotKeyPushEvent;
import com.ispengya.hotkey.remoting.message.HotKeyViewMessage;
import com.ispengya.hotkey.remoting.protocol.Command;
import com.ispengya.hotkey.remoting.protocol.CommandType;
import com.ispengya.hotkey.remoting.protocol.Serializer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端入站处理器。
 *
 * <p>收到推送消息时，直接通过 EventBus 发布事件，不再使用 Consumer 回调。</p>
 *
 * @author ispengya
 */
public class ClientInboundHandler extends SimpleChannelInboundHandler<Command> {

    private static final Logger log = LoggerFactory.getLogger(ClientInboundHandler.class);

    private static final Map<Long, CompletableFuture<Command>> FUTURES = new ConcurrentHashMap<>();

    private final EventBus eventBus;
    private final Serializer serializer;

    public ClientInboundHandler(EventBus eventBus, Serializer serializer) {
        this.eventBus = eventBus;
        this.serializer = serializer;
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

        // 同步响应
        if (requestId != 0L) {
            CompletableFuture<Command> future = FUTURES.remove(requestId);
            if (future != null) {
                future.complete(msg);
                return;
            }
        }

        // 推送消息 -> 发布到 EventBus
        if (msg.getType() == CommandType.HOT_KEY_PUSH) {
            try {
                HotKeyViewMessage message = serializer.deserialize(msg.getPayload(), HotKeyViewMessage.class);
                eventBus.post(new HotKeyPushEvent(message));
            } catch (Exception e) {
                log.error("Failed to deserialize push message", e);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Channel exception", cause);
        ctx.close();
    }
}
