package com.ispengya.hotkey.remoting.protocol;

/**
 * Command 表示一次 remoting 请求或响应的通用命令模型。
 *
 * <p>命令由 {@link CommandType} 和二进制负载 payload 组成，负载由上层
 * 通过 {@code Serializer} 进行序列化和反序列化。</p>
 */
public final class Command {

    private final CommandType type;

    private final long requestId;

    private final byte[] payload;

    public Command(CommandType type, byte[] payload) {
        this(type, 0L, payload);
    }

    public Command(CommandType type, long requestId, byte[] payload) {
        this.type = type;
        this.requestId = requestId;
        this.payload = payload;
    }

    public CommandType getType() {
        return type;
    }

    public long getRequestId() {
        return requestId;
    }

    public byte[] getPayload() {
        return payload;
    }
}
