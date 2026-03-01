package com.ispengya.hotkey.server.remoting;

import com.ispengya.hotkey.remoting.protocol.Command;
import com.ispengya.hotkey.remoting.protocol.CommandType;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PingRequestHandler implements RequestHandler {

    private static final Logger log = LoggerFactory.getLogger(PingRequestHandler.class);
    private final boolean debugEnabled;

    public PingRequestHandler(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Command command) {
        Command response = new Command(CommandType.ADMIN_PING, command.getRequestId(), null);
        ctx.writeAndFlush(response);
        if (debugEnabled && log.isDebugEnabled()) {
            log.debug("Handle ping. remote={}", ctx.channel().remoteAddress());
        }
    }
}
