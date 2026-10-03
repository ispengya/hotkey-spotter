package com.ispengya.hotkey.cli.detect;

import java.util.Map;

/**
 * 访问计数器抽象：累积 key 的访问次数，并周期 drain 出去上报。
 *
 * <p>两个实现分别对应两种上报策略：
 * <ul>
 *     <li>{@link DoubleBufferCounterCollector} — 双 map 无锁（默认）</li>
 *     <li>{@link LockedCounterCollector} — 单 map + 锁</li>
 * </ul>
 * 由 {@link CounterMode} 选择，用于性能对比。</p>
 *
 * @author ispengya
 */
interface AccessCounterCollector {

    /**
     * 记录一次 key 访问。
     *
     * @param key 访问的 key，可为 null（忽略）
     */
    void record(String key);

    /**
     * 清空并返回当前累计的访问计数。调用后计数器归零。
     *
     * @return key -> 访问次数
     */
    Map<String, Integer> drain();
}
