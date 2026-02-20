package com.ispengya.hotkey.cli.detect;

import com.ispengya.hkcache.remoting.client.HotKeyRemotingClient;
import com.ispengya.hkcache.remoting.message.AccessReportMessage;
import com.ispengya.hkcache.remoting.message.HotKeyViewMessage;

import java.util.Map;
import java.util.function.Consumer;

/**
 * HotKeyTransport 封装与 HotKey Server 的通信能力。
 *
 * <p>负责上报访问数据、查询热 Key 视图以及注册推送监听器，
 * 不参与本地缓存和业务决策。</p>
 */
public class HotKeyTransport {

    private final HotKeyRemotingClient remotingClient;
    private final String appName;

    /**
     * 基于底层 Remoting Client 构造通信封装。
     *
     * @param remotingClient 底层 Remoting 客户端
     * @param appName        应用名称
     */
    public HotKeyTransport(HotKeyRemotingClient remotingClient, String appName) {
        this.remotingClient = remotingClient;
        this.appName = appName;
    }

    /**
     * 上报访问统计数据到 Server。
     *
     * @param message 访问统计上报消息
     */
    public void reportAccess(AccessReportMessage message) {
        message.setAppName(appName);
        remotingClient.reportAccess(message);
    }

    /**
     * 查询所有实例的最新热 Key 视图。
     *
     * @param lastVersions  客户端缓存的各实例最新版本号
     * @param timeoutMillis 查询超时时间（毫秒）
     * @return 热 Key 视图消息
     */
    public HotKeyViewMessage queryAllHotKeys(Map<String, Long> lastVersions, long timeoutMillis) {
        return remotingClient.queryAllHotKeys(lastVersions, timeoutMillis);
    }

    /**
     * 注册服务端推送 HotKey 视图的监听器。
     *
     * @param listener 推送消息回调处理器
     */
    public void setPushListener(Consumer<HotKeyViewMessage> listener) {
        remotingClient.setPushListener(listener);
    }
}
