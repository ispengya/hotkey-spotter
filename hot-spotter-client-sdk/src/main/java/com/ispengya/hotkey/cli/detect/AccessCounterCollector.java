package com.ispengya.hotkey.cli.detect;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 双缓冲无锁访问计数器。
 *
 * <p>使用两个 {@link ConcurrentHashMap} 交替读写，减少锁竞争。
 * 写入时按 seq 的低位选择 map0 或 map1，drain 时翻转并清空另一个。</p>
 *
 * @author ispengya
 */
final class AccessCounterCollector {

    private final ConcurrentHashMap<String, LongAdder> map0 = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> map1 = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    void record(String key) {
        if (key == null) {
            return;
        }
        if ((seq.get() & 1L) == 0L) {
            map0.computeIfAbsent(key, k -> new LongAdder()).increment();
        } else {
            map1.computeIfAbsent(key, k -> new LongAdder()).increment();
        }
    }

    Map<String, Integer> drain() {
        long v = seq.incrementAndGet();
        ConcurrentHashMap<String, LongAdder> target = (v & 1L) == 0L ? map1 : map0;
        Map<String, Integer> result = new HashMap<>();
        target.forEach((key, adder) -> {
            long count = adder.sumThenReset();
            if (count > 0L) {
                result.put(key, (int) count);
            }
        });
        target.entrySet().removeIf(e -> e.getValue().sum() == 0L);
        return result;
    }
}
