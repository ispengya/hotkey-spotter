package com.ispengya.hkcache.remoting.client;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * NettyClientConfig 封装 Netty 客户端相关配置。
 *
 * <p>包括 server 地址列表、连接超时、工作线程数以及最大帧长度等。</p>
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

    /**
     * push 通道连接池大小。
     */
    private final int pushPoolSize;

    /**
     * report 通道连接池大小。
     */
    private final int reportPoolSize;

    /**
     * 构造 Netty 客户端配置。
     */
    public NettyClientConfig(List<InetSocketAddress> serverAddresses,
                             int connectTimeoutMillis,
                             int workerThreads,
                             int maxFrameLength) {
        this(serverAddresses, connectTimeoutMillis, workerThreads, maxFrameLength, 1, 2, null);
    }

    public NettyClientConfig(List<InetSocketAddress> serverAddresses,
                             int connectTimeoutMillis,
                             int workerThreads,
                             int maxFrameLength,
                             int pushPoolSize,
                             int reportPoolSize,
                             String appName) {
        this.serverAddresses = serverAddresses;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.workerThreads = workerThreads;
        this.maxFrameLength = maxFrameLength;
        this.pushPoolSize = pushPoolSize;
        this.reportPoolSize = reportPoolSize;
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

    public int getPushPoolSize() {
        return pushPoolSize;
    }

    public int getReportPoolSize() {
        return reportPoolSize;
    }

    public String getAppName() {
        return appName;
    }
}
