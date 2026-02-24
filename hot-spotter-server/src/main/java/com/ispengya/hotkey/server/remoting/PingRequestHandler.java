package com.ispengya.hotkey.server.remoting;

import com.ispengya.hotkey.remoting.protocol.Command;
import com.ispengya.hotkey.remoting.protocol.CommandType;
import io.netty.channel.ChannelHandlerContext;

public final class PingRequestHandler implements RequestHandler {

    @Override
    public void handle(ChannelHandlerContext ctx, Command command) {
        Command response = new Command(CommandType.ADMIN_PING, command.getRequestId(), null);
        ctx.writeAndFlush(response);
    }
}
