package com.ispengya.hotkey.remoting.server;

import com.ispengya.hotkey.remoting.protocol.Command;
import io.netty.channel.ChannelHandlerContext;

/**
 * ServerRequestDispatcher 定义服务端请求分发接口。
 *
 * <p>实现类根据 {@link Command#getType()} 决定将请求路由到哪个具体的
 * 业务处理逻辑。</p>
 */
public interface ServerRequestDispatcher {

    /**
     * 分发请求到具体业务处理逻辑。
     *
     * @param ctx     Netty 上下文
     * @param command 收到的命令
     */
    void dispatch(ChannelHandlerContext ctx, Command command);
}
