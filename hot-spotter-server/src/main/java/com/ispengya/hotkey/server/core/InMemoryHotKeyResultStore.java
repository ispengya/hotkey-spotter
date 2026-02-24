package com.ispengya.hotkey.server.core;

import com.ispengya.hotkey.server.model.HotKeyResult;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * InMemoryHotKeyResultStore 基于内存的热 Key 结果存储实现。
 *
 * @author ispengya
 */
public final class InMemoryHotKeyResultStore implements HotKeyResultStore {

    private final ConcurrentMap<String, HotKeyResult> store = new ConcurrentHashMap<>();

    @Override
    public void update(HotKeyResult result) {
        store.put(result.getAppName(), result);
    }

    @Override
    public HotKeyResult get(String appName) {
        return store.get(appName);
    }

    @Override
    public Iterable<HotKeyResult> listAll() {
        return store.values();
    }
}
