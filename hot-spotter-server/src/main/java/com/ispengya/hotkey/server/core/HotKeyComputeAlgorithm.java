package com.ispengya.hotkey.server.core;

/**
 * HotKeyComputeAlgorithm 定义滑动窗口下的热 Key 判定算法。
 *
 * <p>在给定时间窗口内，如果某个 key 的访问总数
 * 大于等于阈值，则判定为热 key。时间窗口由 {@link SlidingWindowInstanceAggStore} 的
 * 配置决定，算法本身只依赖聚合后的统计数据。</p>
 *
 * <p>支持基于规则的动态阈值配置，阈值通过 {@link RuleManager} 按 appName 维度查找。</p>
 *
 * @author ispengya
 */
public final class HotKeyComputeAlgorithm {

    /**
     * 默认最小访问次数阈值。
     */
    private final long defaultMinCountThreshold;

    /**
     * 规则管理器，提供按 appName 维度的规则查找。
     */
    private final RuleManager ruleManager;

    /**
     * 构造滑动窗口热 Key 判定算法。
     *
     * @param defaultMinCountThreshold 默认访问次数阈值
     * @param ruleManager              规则管理器
     */
    public HotKeyComputeAlgorithm(long defaultMinCountThreshold,
                                  RuleManager ruleManager) {
        this.defaultMinCountThreshold = defaultMinCountThreshold;
        this.ruleManager = ruleManager;
    }

    /**
     * 判断是否为热 key。
     *
     * @param appName 应用名称
     * @param stat    聚合统计数据
     * @return 是热 key 返回 true
     */
    public boolean isHot(String appName, AggregatedKeyStat stat) {
        if (stat == null || appName == null) {
            return false;
        }

        long threshold = ruleManager.getThreshold(appName, stat.getKey());
        return stat.getTotalCount() >= threshold;
    }
}
