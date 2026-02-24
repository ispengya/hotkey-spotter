package com.ispengya.hotkey.cli.spring;

import com.ispengya.hotkey.cli.core.HotKeyClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HotKeyClientManager 负责集中管理按实例名称划分的 HotKeyClient。
 *
 * 业务方可以通过实例名称获取对应实例的客户端。
 *
 * @author ispengya
 */
@Deprecated
public class HotKeyClientManager {

    private final Map<String, HotKeyClient> clients = new ConcurrentHashMap<>();
    private volatile HotKeyClient defaultClient;

    /**
     * 注册一个 HotKeyClient。
     *
     * @param instanceName 实例名称
     * @param client       客户端实例
     */
    public void register(String instanceName, HotKeyClient client) {
        if (client == null) {
            return;
        }
        String key = buildKey(instanceName);
        clients.put(key, client);
        if (defaultClient == null) {
            defaultClient = client;
        }
    }

    /**
     * 按实例名称获取 HotKeyClient。
     *
     * @param instanceName 实例名称
     * @return 对应的 HotKeyClient，找不到时返回 null
     */
    public HotKeyClient get(String instanceName) {
        String key = buildKey(instanceName);
        return clients.get(key);
    }

    /**
     * 获取默认的 HotKeyClient。
     *
     * @return 默认客户端
     */
    public HotKeyClient getDefaultClient() {
        return defaultClient;
    }

    private String buildKey(String instanceName) {
        return (instanceName == null || instanceName.isEmpty()) ? "default" : instanceName;
    }
}
