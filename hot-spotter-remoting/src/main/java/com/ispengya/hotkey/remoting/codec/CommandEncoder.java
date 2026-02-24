package com.ispengya.hotkey.remoting.codec;

import com.ispengya.hotkey.remoting.protocol.Command;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class CommandEncoder extends MessageToByteEncoder<Command> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Command msg, ByteBuf out) {
        byte[] payload = msg.getPayload();
        int payloadLength = payload == null ? 0 : payload.length;

        out.writeInt(4 + 8 + payloadLength);

        out.writeInt(msg.getType().ordinal());
        out.writeLong(msg.getRequestId());

        if (payload != null) {
            out.writeBytes(payload);
        }
    }
}
