package com.ispengya.hotkey.server.core;

import java.util.Map;

/**
 * WindowSlot 表示滑动窗口中的一个时间槽。
 *
 * <p>每个槽对应一个时间段（如 1 秒），存储该时间段内的 key 访问统计。</p>
 *
 * @author ispengya
 */
public final class WindowSlot {

    /**
     * 窗口槽的开始时间戳（毫秒）。
     */
    private final long windowStartMillis;

    /**
     * 该槽内的统计数据：key -> AggregatedKeyStat。
     */
    private final Map<String, AggregatedKeyStat> stats;

    /**
     * 构造窗口槽。
     *
     * @param windowStartMillis 槽开始时间
     * @param stats             统计数据 Map
     */
    public WindowSlot(long windowStartMillis,
                      Map<String, AggregatedKeyStat> stats) {
        this.windowStartMillis = windowStartMillis;
        this.stats = stats;
    }

    public long getWindowStartMillis() {
        return windowStartMillis;
    }

    public Map<String, AggregatedKeyStat> getStats() {
        return stats;
    }
}
