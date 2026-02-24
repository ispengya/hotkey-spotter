package com.ispengya.hotkey.remoting.server;

/**
 * NettyServerConfig 封装 Netty 服务端相关配置参数。
 *
 * <p>包括监听端口、事件循环线程数、连接超时、空闲检测时间以及最大帧长度等，
 * 便于在不同环境下灵活调整。</p>
 */
public final class NettyServerConfig {

    /**
     * 服务端监听端口。
     */
    private final int port;

    /**
     * boss 线程数，用于接收连接。
     */
    private final int bossThreads;

    /**
     * worker 线程数，用于处理读写事件。
     */
    private final int workerThreads;

    /**
     * TCP backlog 配置。
     */
    private final int soBacklog;

    /**
     * 连接超时时间（毫秒）。
     */
    private final int connectTimeoutMillis;

    /**
     * 读空闲时间（秒）。
     */
    private final int readIdleSeconds;

    /**
     * 写空闲时间（秒）。
     */
    private final int writeIdleSeconds;

    /**
     * 全部空闲时间（秒）。
     */
    private final int allIdleSeconds;

    /**
     * 单帧允许的最大长度。
     */
    private final int maxFrameLength;

    /**
     * 构造 Netty 服务端配置。
     */
    public NettyServerConfig(int port,
                             int bossThreads,
                             int workerThreads,
                             int soBacklog,
                             int connectTimeoutMillis,
                             int readIdleSeconds,
                             int writeIdleSeconds,
                             int allIdleSeconds,
                             int maxFrameLength) {
        this.port = port;
        this.bossThreads = bossThreads;
        this.workerThreads = workerThreads;
        this.soBacklog = soBacklog;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readIdleSeconds = readIdleSeconds;
        this.writeIdleSeconds = writeIdleSeconds;
        this.allIdleSeconds = allIdleSeconds;
        this.maxFrameLength = maxFrameLength;
    }

    /**
     * 获取监听端口。
     */
    public int getPort() {
        return port;
    }

    /**
     * 获取 boss 线程数。
     */
    public int getBossThreads() {
        return bossThreads;
    }

    /**
     * 获取 worker 线程数。
     */
    public int getWorkerThreads() {
        return workerThreads;
    }

    /**
     * 获取 backlog 大小。
     */
    public int getSoBacklog() {
        return soBacklog;
    }

    /**
     * 获取连接超时时间（毫秒）。
     */
    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public int getReadIdleSeconds() {
        return readIdleSeconds;
    }

    public int getWriteIdleSeconds() {
        return writeIdleSeconds;
    }

    public int getAllIdleSeconds() {
        return allIdleSeconds;
    }

    public int getMaxFrameLength() {
        return maxFrameLength;
    }
}
