package com.ispengya.hotkey.cli.detect;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 单 map + 锁访问计数器。
 *
 * <p>使用一个 {@link HashMap} 和一把 {@link ReentrantLock}，record 与 drain 都在同一把锁下进行。
 * {@link #drain()} 遍历并清空计数期间会阻塞所有写入线程。
 * 用于与 {@link DoubleBufferCounterCollector} 做性能对比。</p>
 *
 * @author ispengya
 */
final class LockedCounterCollector implements AccessCounterCollector {

    private final Map<String, LongAdder> map = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public void record(String key) {
        if (key == null) {
            return;
        }
        lock.lock();
        try {
            map.computeIfAbsent(key, k -> new LongAdder()).increment();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Map<String, Integer> drain() {
        lock.lock();
        try {
            Map<String, Integer> result = new HashMap<>(map.size());
            for (Map.Entry<String, LongAdder> e : map.entrySet()) {
                long count = e.getValue().sumThenReset();
                if (count > 0L) {
                    result.put(e.getKey(), (int) count);
                }
            }
            map.entrySet().removeIf(e -> e.getValue().sum() == 0L);
            return result;
        } finally {
            lock.unlock();
        }
    }
}
