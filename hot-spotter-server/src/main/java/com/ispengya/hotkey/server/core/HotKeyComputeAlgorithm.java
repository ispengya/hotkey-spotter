package com.ispengya.hotkey.server.core;

import com.ispengya.hotkey.common.rule.KeyRule;
import com.ispengya.hotkey.common.rule.RuleConfig;

/**
 * HotKeyComputeAlgorithm 定义滑动窗口下的热 Key 判定算法。
 *
 * <p>在给定时间窗口内，如果某个 key 的访问总数
 * 大于等于阈值，则判定为热 key。时间窗口由 {@link SlidingWindowInstanceAggStore} 的
 * 配置决定，算法本身只依赖聚合后的统计数据。</p>
 *
 * <p>支持基于规则的动态阈值配置。</p>
 */
public final class HotKeyComputeAlgorithm {

    /**
     * 默认最小访问次数阈值。
     */
    private final long defaultMinCountThreshold;

    /**
     * 规则配置（可选，用于动态阈值）。
     */
    private volatile RuleConfig ruleConfig;

    /**
     * 构造滑动窗口热 Key 判定算法。
     *
     * @param defaultMinCountThreshold 默认访问次数阈值
     */
    public HotKeyComputeAlgorithm(long defaultMinCountThreshold) {
        this.defaultMinCountThreshold = defaultMinCountThreshold;
    }

    /**
     * 更新规则配置。
     */
    public void updateRuleConfig(RuleConfig ruleConfig) {
        this.ruleConfig = ruleConfig;
    }

    /**
     * 判断是否为热 key。
     */
    public boolean isHot(AggregatedKeyStat stat) {
        if (stat == null) {
            return false;
        }

        // 尝试从规则中获取阈值
        long threshold = getThreshold(stat.getKey());
        return stat.getTotalCount() >= threshold;
    }

    /**
     * 获取指定 key 的阈值。
     */
    private long getThreshold(String key) {
        RuleConfig config = ruleConfig;
        if (config != null && key != null) {
            KeyRule rule = config.findRule(key);
            if (rule != null) {
                return rule.getThreshold();
            }
        }
        return defaultMinCountThreshold;
    }
}
