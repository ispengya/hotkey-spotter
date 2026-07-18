package com.ispengya.hotkey.cli.spring;

import com.ispengya.hotkey.cli.cache.CacheHolder;
import com.ispengya.hotkey.cli.core.HotKeyClient;
import com.ispengya.hotkey.cli.detect.HotKeyReporter;

/**
 * HotKeyClientFactory 封装 HotKeyClient 的创建逻辑，
 * 便于在 Spring 自动装配或多实例场景下复用构建过程。
 *
 * @author ispengya
 */
public class HotKeyClientFactory {

    public HotKeyClient createHotKeyClient(HotKeyReporter reporter, String appName) {
        CacheHolder cacheHolder = new CacheHolder(appName);
        HotKeyClient client = new HotKeyClient(reporter, cacheHolder);

        // 设置热 key 监听器，将推送事件转发给 HotKeyClient
        reporter.setHotKeyListener(new HotKeyReporter.HotKeyListener() {
            @Override
            public void onHotKeyAdded(String key, int durationSeconds) {
                client.addHotKey(key, durationSeconds);
            }

            @Override
            public void onHotKeyRemoved(String key) {
                client.removeHotKey(key);
            }
        });

        return client;
    }
}
