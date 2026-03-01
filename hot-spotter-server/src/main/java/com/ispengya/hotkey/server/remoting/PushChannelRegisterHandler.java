package com.ispengya.hotkey.server.remoting;

import com.ispengya.hotkey.remoting.message.PushChannelRegisterMessage;
import com.ispengya.hotkey.remoting.protocol.Command;
import com.ispengya.hotkey.remoting.protocol.CommandType;
import com.ispengya.hotkey.remoting.protocol.Serializer;
import com.ispengya.hotkey.remoting.server.ServerChannelManager;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PushChannelRegisterHandler implements RequestHandler {

    private static final Logger log = LoggerFactory.getLogger(PushChannelRegisterHandler.class);

    private final ServerChannelManager channelManager;
    private final Serializer serializer;

    public PushChannelRegisterHandler(ServerChannelManager channelManager,
                                      Serializer serializer) {
        this.channelManager = channelManager;
        this.serializer = serializer;
    }

    @Override
    public void handle(ChannelHandlerContext ctx, Command command) {
        try {
            PushChannelRegisterMessage message = serializer.deserialize(command.getPayload(), PushChannelRegisterMessage.class);
            if (message == null) {
                return;
            }
            channelManager.registerPushChannel(message.getAppName(), ctx.channel());
            Command response = new Command(CommandType.PUSH_CHANNEL_REGISTER, command.getRequestId(), null);
            ctx.writeAndFlush(response);
            if (log.isInfoEnabled()) {
                log.info("Register push channel. appName={}, remote={}",
                        message.getAppName(), ctx.channel().remoteAddress());
            }
        } catch (Exception ignored) {
        }
    }
}
