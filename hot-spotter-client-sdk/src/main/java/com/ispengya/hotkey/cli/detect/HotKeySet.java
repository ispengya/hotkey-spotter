package com.ispengya.hotkey.cli.detect;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HotKeySet {

    private static final Logger log = LoggerFactory.getLogger(HotKeySet.class);

    private volatile Set<String> hotKeys = Collections.emptySet();

    public synchronized boolean add(String key) {
        if (key == null) {
            return false;
        }
        Set<String> merged = new HashSet<>(hotKeys);
        boolean changed = merged.add(key);
        if (!changed) {
            return false;
        }
        hotKeys = Collections.unmodifiableSet(merged);
        if (log.isInfoEnabled()) {
            log.info("Add hot key. key={}, size={}", key, merged.size());
        }
        return true;
    }

    public synchronized boolean remove(String key) {
        if (key == null) {
            return false;
        }
        if (hotKeys.isEmpty()) {
            return false;
        }
        Set<String> merged = new HashSet<>(hotKeys);
        boolean changed = merged.remove(key);
        if (!changed) {
            return false;
        }
        hotKeys = Collections.unmodifiableSet(merged);
        if (log.isInfoEnabled()) {
            log.info("Remove hot key. key={}, size={}", key, merged.size());
        }
        return true;
    }

    public boolean contains(String key) {
        if (key == null) {
            return false;
        }
        return hotKeys.contains(key);
    }
}
