package com.ispengya.hotkey.server.scheduler;

import com.ispengya.hotkey.server.core.HotKeyResultStore;
import com.ispengya.hotkey.server.model.HotKeyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

final class HotKeyDecayTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(HotKeyDecayTask.class);
    private final String appName;
    private final HotKeyResultStore resultStore;
    private final long idleMillis;
    private final HotKeyChangePublisher changePublisher;
    private final boolean debugEnabled;

    HotKeyDecayTask(String appName,
                    HotKeyResultStore resultStore,
                    long idleMillis,
                    HotKeyChangePublisher changePublisher,
                    boolean debugEnabled) {
        this.appName = appName;
        this.resultStore = resultStore;
        this.idleMillis = idleMillis;
        this.changePublisher = changePublisher;
        this.debugEnabled = debugEnabled;
    }

    @Override
    public void run() {
        HotKeyResult previous = resultStore.get(appName);
        if (previous == null) {
            return;
        }
        Set<String> previousKeys = previous.getHotKeys();
        if (previousKeys == null || previousKeys.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        Set<String> newHotKeys = new HashSet<>(previousKeys);
        Set<String> removedKeys = new HashSet<>();
        boolean changed = false;

        for (String key : previousKeys) {
            if (key == null) {
                continue;
            }
            long lastActive = HotKeyComputeTask.getLastActiveTime(appName, key);
            if (lastActive <= 0L || now - lastActive >= idleMillis) {
                newHotKeys.remove(key);
                HotKeyComputeTask.removeKey(appName, key);
                removedKeys.add(key);
                changed = true;
                if (debugEnabled && log.isDebugEnabled()) {
                    log.debug("Decay hot key due to idle. appName={}, key={}, idleMillis={}",
                            appName, key, idleMillis);
                }
            }
        }

        if (!changed) {
            return;
        }

        HotKeyResult result = HotKeyResult.from(previous.getAppName(), newHotKeys);
        resultStore.update(result);
        if (log.isInfoEnabled()) {
            log.info("Hot key decay completed. appName={}, removedCount={}, remainHotSize={}",
                    appName, removedKeys.size(), newHotKeys.size());
        }
        changePublisher.publish(result, null, removedKeys);
    }
}
