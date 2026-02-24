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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class HotKeyComputeTask {

    private static final Logger log = LoggerFactory.getLogger(HotKeyComputeTask.class);
    private static final ConcurrentMap<String, InstanceHotState> STATE = new ConcurrentHashMap<>();

    private final String appName;
    private final InstanceWindowRegistry aggregator;
    private final HotKeyComputeAlgorithm algorithm;
    private final HotKeyResultStore resultStore;
    private final HotKeyChangePublisher changePublisher;

    public HotKeyComputeTask(String appName,
                             InstanceWindowRegistry aggregator,
                             HotKeyComputeAlgorithm algorithm,
                             HotKeyResultStore resultStore,
                             HotKeyChangePublisher changePublisher) {
        this.appName = appName;
        this.aggregator = aggregator;
        this.algorithm = algorithm;
        this.resultStore = resultStore;
        this.changePublisher = changePublisher;
    }

    public static void computeAndPublish(String appName,
                                         String key,
                                         InstanceWindowRegistry aggregator,
                                         HotKeyComputeAlgorithm algorithm,
                                         HotKeyResultStore resultStore,
                                         HotKeyChangePublisher changePublisher) {
        if (key == null) {
            return;
        }
        AggregatedKeyStat stat = aggregator
                .selectWindowForApp(appName)
                .snapshotForKey(key);
        if (!algorithm.isHot(stat)) {
            if (log.isDebugEnabled()) {
                log.debug("Key not hot, skip publish. appName={}, key={}", appName, key);
            }
            return;
        }
        long now = System.currentTimeMillis();
        recordHot(appName, key, 1.0d, now);

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
        changePublisher.publish(result, Collections.singleton(key), null);
    }

    static long getLastActiveTime(String instanceId, String key) {
        InstanceHotState state = STATE.get(instanceId);
        if (state == null) {
            return 0L;
        }
        HotKeyEntry entry = state.entries.get(key);
        if (entry == null) {
            return 0L;
        }
        return entry.lastActiveTimeMillis;
    }

    static void removeKey(String instanceId, String key) {
        InstanceHotState state = STATE.get(instanceId);
        if (state == null) {
            return;
        }
        state.entries.remove(key);
    }

    private static void recordHot(String instanceId, String key, double deltaScore, long nowMillis) {
        InstanceHotState state = STATE.computeIfAbsent(instanceId, InstanceHotState::new);
        HotKeyEntry entry = state.entries.computeIfAbsent(key, HotKeyEntry::new);
        entry.score += deltaScore;
        entry.lastActiveTimeMillis = nowMillis;
    }

    private static final class InstanceHotState {

        private final String instanceId;
        private final ConcurrentMap<String, HotKeyEntry> entries = new ConcurrentHashMap<>();

        private InstanceHotState(String instanceId) {
            this.instanceId = instanceId;
        }
    }

    private static final class HotKeyEntry {

        private final String key;
        private double score;
        private volatile long lastActiveTimeMillis;

        private HotKeyEntry(String key) {
            this.key = key;
        }
    }
}
