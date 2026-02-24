package com.ispengya.hotkey.server.remoting;

import com.ispengya.hotkey.remoting.protocol.Command;
import com.ispengya.hotkey.remoting.protocol.CommandType;
import com.ispengya.hotkey.remoting.server.ServerRequestDispatcher;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DefaultServerRequestDispatcher 默认的服务端请求分发器实现。
 *
 * <p>维护命令类型到 Handler 的映射关系，将请求路由到注册的 Handler。</p>
 *
 * @author ispengya
 */
public final class DefaultServerRequestDispatcher implements ServerRequestDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultServerRequestDispatcher.class);

    private final Map<CommandType, RequestHandler> handlers = new ConcurrentHashMap<>();

    /**
     * 注册请求处理器。
     *
     * @param type    命令类型
     * @param handler 处理器实例
     */
    public void registerHandler(CommandType type, RequestHandler handler) {
        handlers.put(type, handler);
    }

    @Override
    public void dispatch(ChannelHandlerContext ctx, Command command) {
        RequestHandler handler = handlers.get(command.getType());
        if (handler != null) {
            handler.handle(ctx, command);
        } else {
            log.warn("No handler found for command type: {}", command.getType());
        }
    }
}
