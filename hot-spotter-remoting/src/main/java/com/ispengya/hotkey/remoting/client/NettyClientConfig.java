package com.ispengya.hotkey.remoting.client;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * NettyClientConfig 封装 Netty 客户端相关配置。
 *
 * @author ispengya
 */
public final class NettyClientConfig {

    private final List<InetSocketAddress> serverAddresses;

    private final String appName;

    /**
     * 连接超时时间（毫秒）。
     */
    private final int connectTimeoutMillis;

    /**
     * worker 线程数。
     */
    private final int workerThreads;

    /**
     * 单帧允许的最大长度。
     */
    private final int maxFrameLength;

    public NettyClientConfig(List<InetSocketAddress> serverAddresses,
                             int connectTimeoutMillis,
                             int workerThreads,
                             int maxFrameLength,
                             String appName) {
        this.serverAddresses = serverAddresses;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.workerThreads = workerThreads;
        this.maxFrameLength = maxFrameLength;
        this.appName = appName;
    }

    public List<InetSocketAddress> getServerAddresses() {
        return serverAddresses;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public int getMaxFrameLength() {
        return maxFrameLength;
    }

    public String getAppName() {
        return appName;
    }
}
