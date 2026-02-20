package com.ispengya.hkcache.server.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * InstanceWindowRegistry 负责维护 appName 到滑动窗口存储的映射。
 *
 * 核心职责：
 * 1. 根据 appName 选择对应的滑动窗口（不存在时创建）
 * 2. 枚举当前所有已存在窗口对应的 appName
 */
public final class InstanceWindowRegistry implements InstanceRegistry {

    private final ConcurrentMap<String, SlidingWindowInstanceAggStore> stores = new ConcurrentHashMap<>();

    private final long windowSizeMillis;
    private final int windowSlotCount;

    public InstanceWindowRegistry(long windowSizeMillis,
                                  int windowSlotCount) {
        this.windowSizeMillis = windowSizeMillis;
        this.windowSlotCount = windowSlotCount;
    }

    /**
     * 根据 appName 选择对应的滑动窗口，如果不存在则创建一个新的窗口。
     *
     * @param appName 应用名
     * @return 该应用对应的滑动窗口存储
     */
    public SlidingWindowInstanceAggStore selectWindowForApp(String appName) {
        return stores.computeIfAbsent(appName, this::createWindowForApp);
    }

    @Override
    public Set<String> listAppNames() {
        return stores.keySet();
    }

    private SlidingWindowInstanceAggStore createWindowForApp(String appName) {
        List<WindowSlot> slots = new ArrayList<>();
        long now = System.currentTimeMillis();
        long alignedStart = now - (now % windowSizeMillis);
        for (int i = 0; i < windowSlotCount; i++) {
            long start = alignedStart - (long) i * windowSizeMillis;
            slots.add(new WindowSlot(start, new ConcurrentHashMap<>()));
        }
        return new SlidingWindowInstanceAggStore(windowSizeMillis, windowSlotCount, slots);
    }
}
