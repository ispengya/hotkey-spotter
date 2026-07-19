package com.ispengya.hotkey.server.scheduler;

import com.ispengya.hotkey.server.core.AggregatedKeyStat;
import com.ispengya.hotkey.server.core.HotKeyComputeAlgorithm;
import com.ispengya.hotkey.server.core.HotKeyResultStore;
import com.ispengya.hotkey.server.core.InstanceWindowRegistry;
import com.ispengya.hotkey.server.model.HotKeyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 热 Key 计算任务，判定单 key 是否热点并发布变更。
 */
public final class HotKeyComputeTask {

    private static final Logger log = LoggerFactory.getLogger(HotKeyComputeTask.class);

    private HotKeyComputeTask() {
    }

    /**
     * 对单 key 做快照计算，命中阈值则写入 resultStore 并发布变更。
     */
    public static void computeAndPublish(String appName,
                                         String key,
                                         InstanceWindowRegistry windowRegistry,
                                         HotKeyComputeAlgorithm algorithm,
                                         HotKeyResultStore resultStore,
                                         HotKeyChangePublisher changePublisher) {
        if (key == null) {
            return;
        }
        AggregatedKeyStat stat = windowRegistry
                .selectWindowForApp(appName)
                .snapshotForKey(key);
        if (!algorithm.isHot(stat)) {
            return;
        }

        HotKeyResult previous = resultStore.get(appName);
        Set<String> previousKeys = previous == null ? Collections.emptySet() : previous.getHotKeys();

        Set<String> newHotKeys = new HashSet<>(previousKeys);
        newHotKeys.add(key);

        HotKeyResult result = HotKeyResult.from(appName, newHotKeys);
        resultStore.update(result);
        if (log.isInfoEnabled()) {
            log.info("Detect hot key. appName={}, key={}, hotSize={}",
                    appName, key, newHotKeys.size());
        }
        changePublisher.publish(result, key);
    }
}
