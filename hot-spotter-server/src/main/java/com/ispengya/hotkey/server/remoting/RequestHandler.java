package com.ispengya.hotkey.server.remoting;

import com.ispengya.hotkey.remoting.protocol.Command;
import io.netty.channel.ChannelHandlerContext;

/**
 * RequestHandler 定义 Server 端业务请求处理接口。
 *
 * <p>Remoting 层接收到命令后，根据命令类型分发给具体的 Handler 实现。</p>
 *
 * @author ispengya
 */
public interface RequestHandler {

    /**
     * 处理具体的业务命令。
     *
     * @param ctx     Netty 上下文
     * @param command 收到的命令
     */
    void handle(ChannelHandlerContext ctx, Command command);
}
