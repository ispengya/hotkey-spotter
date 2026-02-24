package com.ispengya.hotkey.remoting.codec;

import com.ispengya.hotkey.remoting.protocol.Command;
import com.ispengya.hotkey.remoting.protocol.CommandType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

public class CommandDecoder extends LengthFieldBasedFrameDecoder {

    public CommandDecoder(int maxFrameLength) {
        super(maxFrameLength, 0, 4, 0, 4);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }

        try {
            int typeOrdinal = frame.readInt();
            CommandType type = CommandType.values()[typeOrdinal];

            long requestId = frame.readLong();

            int payloadLength = frame.readableBytes();
            byte[] payload = new byte[payloadLength];
            frame.readBytes(payload);

            return new Command(type, requestId, payload);
        } finally {
            frame.release();
        }
    }
}
