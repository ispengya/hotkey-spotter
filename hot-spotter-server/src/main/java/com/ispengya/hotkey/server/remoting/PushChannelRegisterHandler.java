package com.ispengya.hotkey.server.remoting;

import com.ispengya.hotkey.remoting.message.PushChannelRegisterMessage;
import com.ispengya.hotkey.remoting.protocol.Command;
import com.ispengya.hotkey.remoting.protocol.CommandType;
import com.ispengya.hotkey.remoting.protocol.Serializer;
import com.ispengya.hotkey.remoting.server.ServerChannelManager;
import io.netty.channel.ChannelHandlerContext;

public final class PushChannelRegisterHandler implements RequestHandler {

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
        } catch (Exception ignored) {
        }
    }
}
