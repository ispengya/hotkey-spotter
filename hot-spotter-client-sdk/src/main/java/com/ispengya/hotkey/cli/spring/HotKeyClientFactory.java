package com.ispengya.hotkey.cli.spring;

import com.ispengya.hotkey.cli.cache.CacheHolder;
import com.ispengya.hotkey.cli.core.HotKeyClient;
import com.ispengya.hotkey.cli.detect.HotKeyDetector;

/**
 * HotKeyClientFactory 封装 HotKeyClient 的创建逻辑，
 * 便于在 Spring 自动装配或多实例场景下复用构建过程。
 *
 * @author ispengya
 */
public class HotKeyClientFactory {

    public HotKeyClient createHotKeyClient(HotKeyDetector detector, String appName) {
        CacheHolder cacheHolder = new CacheHolder(appName);
        HotKeyClient client = new HotKeyClient(detector, cacheHolder);

        // 设置热 key 监听器，将推送事件转发给 HotKeyClient
        detector.setHotKeyListener(new HotKeyDetector.HotKeyListener() {
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
