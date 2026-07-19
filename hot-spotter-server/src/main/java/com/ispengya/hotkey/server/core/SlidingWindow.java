package com.ispengya.hotkey.server.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 每个 key 独立的滑动窗口，基于 {@link AtomicLong} 循环数组。
 *
 * <p>窗口大小严格由规则的 duration 和 threshold 决定，与 hotkey 项目的
 * {@code SlidingWindow} 保持一致设计：</p>
 *
 * <pre>
 * duration &lt;= 5s  → windowSize=5, timeMillisPerSlice = duration * 200
 * duration &gt; 5s   → windowSize=10, timeMillisPerSlice = duration * 100
 * </pre>
 *
 * <p>线程安全：{@link #addCount(long)} 方法加 {@code synchronized}，
 * 但锁粒度是单个 key 的窗口实例，不同 key 之间完全无竞争。</p>
 *
 * @author ispengya
 */
public final class SlidingWindow {

    /**
     * 循环队列，长度 = windowSize * 2
     */
    private final AtomicLong[] timeSlices;

    /**
     * 队列总长度。
     */
    private final int timeSliceSize;

    /**
     * 每个时间片的时长（毫秒）。
     */
    private final int timeMillisPerSlice;

    /**
     * 窗口内时间片个数。
     */
    private final int windowSize;

    /**
     * 在完整窗口期内允许通过的最大阈值。
     */
    private final int threshold;

    /**
     * 窗口起始时间戳（第一个数据进来的时间）。
     */
    private long beginTimestamp;

    /**
     * 最后一个数据进来的时间戳。
     */
    private long lastAddTimestamp;

    /**
     * @param duration  时间窗口（秒），来自 {@link com.ispengya.hotkey.common.rule.KeyRule#getInterval()}
     * @param threshold 访问次数阈值，来自 {@link com.ispengya.hotkey.common.rule.KeyRule#getThreshold()}
     */
    public SlidingWindow(int duration, int threshold) {
        // 超过10分钟的按10分钟
        if (duration > 600) {
            duration = 600;
        }
        if (duration <= 5) {
            this.windowSize = 5;
            this.timeMillisPerSlice = duration * 200;
        } else {
            this.windowSize = 10;
            this.timeMillisPerSlice = duration * 100;
        }
        this.threshold = threshold;
        // 保证至少两个完整窗口
        this.timeSliceSize = windowSize * 2;

        this.timeSlices = new AtomicLong[timeSliceSize];
        for (int i = 0; i < timeSliceSize; i++) {
            timeSlices[i] = new AtomicLong(0);
        }
        this.beginTimestamp = System.currentTimeMillis();
        this.lastAddTimestamp = beginTimestamp;
    }

    /**
     * 增加 count 次访问，返回是否达到阈值（hot）。
     *
     * @param count 访问次数
     * @return true 表示达到阈值，应判定为热 key
     */
    public synchronized boolean addCount(long count) {
        int index = locationIndex();
        clearFromIndex(index);

        int sum = 0;
        sum += (int) timeSlices[index].addAndGet(count);
        for (int i = 1; i < windowSize; i++) {
            sum += (int) timeSlices[(index - i + timeSliceSize) % timeSliceSize].get();
        }

        lastAddTimestamp = System.currentTimeMillis();

        return sum >= threshold;
    }

    /**
     * 计算当前所在的时间片位置。
     */
    private int locationIndex() {
        long now = System.currentTimeMillis();
        // 如果已经空了一个完整的窗口以上，重置
        if (now - lastAddTimestamp > (long) timeMillisPerSlice * windowSize) {
            reset();
        }

        int index = (int) (((now - beginTimestamp) / timeMillisPerSlice) % timeSliceSize);
        if (index < 0) {
            return 0;
        }
        return index;
    }

    /**
     * 清理当前时间片之后 windowSize 个过期槽的数据。
     */
    private void clearFromIndex(int index) {
        for (int i = 1; i <= windowSize; i++) {
            int j = index + i;
            if (j >= windowSize * 2) {
                j -= windowSize * 2;
            }
            timeSlices[j].set(0);
        }
    }

    private void reset() {
        beginTimestamp = System.currentTimeMillis();
        for (int i = 0; i < timeSliceSize; i++) {
            timeSlices[i].set(0);
        }
    }
}
