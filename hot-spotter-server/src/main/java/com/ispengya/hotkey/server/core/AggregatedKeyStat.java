package com.ispengya.hotkey.server.core;

/**
 * AggregatedKeyStat 封装了单个 key 在某段时间窗口内的聚合统计信息。
 *
 * <p>包括总访问次数、成功次数、失败次数以及 RT 累计值等，
 * 供 {@link HotKeyComputeAlgorithm} 进行热 Key 判定。</p>
 *
 * @author ispengya
 */
public final class AggregatedKeyStat {

    /**
     * 业务 key。
     */
    private final String key;

    /**
     * 总访问次数。
     */
    private final long totalCount;

    /**
     * 成功访问次数。
     */
    private final long successCount;

    /**
     * 失败访问次数。
     */
    private final long failCount;

    /**
     * 总响应耗时（毫秒）。
     */
    private final long totalRtMillis;

    /**
     * 构造聚合统计对象。
     *
     * @param key           业务 key
     * @param totalCount    总次数
     * @param successCount  成功次数
     * @param failCount     失败次数
     * @param totalRtMillis 总耗时
     */
    public AggregatedKeyStat(String key,
                             long totalCount,
                             long successCount,
                             long failCount,
                             long totalRtMillis) {
        this.key = key;
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failCount = failCount;
        this.totalRtMillis = totalRtMillis;
    }

    public String getKey() {
        return key;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public long getFailCount() {
        return failCount;
    }

    public long getTotalRtMillis() {
        return totalRtMillis;
    }

    /**
     * 计算平均响应耗时。
     *
     * @return 平均 RT（毫秒），若 totalCount 为 0 则返回 0.0
     */
    public double getAvgRtMillis() {
        if (totalCount == 0L) {
            return 0.0d;
        }
        return (double) totalRtMillis / (double) totalCount;
    }
}
