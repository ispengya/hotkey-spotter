package com.ispengya.hkcache.server.core;

/**
 * HotKeyComputeAlgorithm 定义滑动窗口下的热 Key 判定算法。
 *
 * <p>在给定时间窗口内，如果某个 key 的访问总数
 * 大于等于阈值，则判定为热 key。时间窗口由 {@link SlidingWindowInstanceAggStore} 的
 * 配置决定，算法本身只依赖聚合后的统计数据。</p>
 */
public final class HotKeyComputeAlgorithm {

    /**
     * 最小访问次数阈值。
     */
    private final long minCountThreshold;

    /**
     * 构造滑动窗口热 Key 判定算法。
     *
     * @param minCountThreshold 访问次数阈值
     */
    public HotKeyComputeAlgorithm(long minCountThreshold) {
        this.minCountThreshold = minCountThreshold;
    }

    public boolean isHot(AggregatedKeyStat stat) {
        if (stat == null) {
            return false;
        }
        return stat.getTotalCount() >= minCountThreshold;
    }
}
