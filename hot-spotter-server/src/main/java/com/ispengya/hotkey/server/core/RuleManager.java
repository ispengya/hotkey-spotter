package com.ispengya.hotkey.server.core;

import com.ispengya.hotkey.common.rule.KeyRule;
import com.ispengya.hotkey.common.rule.RuleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * RuleManager 管理所有 appName 对应的热 key 探测规则。
 *
 * <p>作为 Nacos 规则配置在 Server 端的统一入口，提供：
 * <ul>
 *     <li>根据 appName + key 判断是否在规则范围内（决定是否计算）</li>
 *     <li>根据 appName + key 获取对应的判定阈值</li>
 *     <li>根据 appName + key 获取热 key 缓存时长</li>
 * </ul>
 *
 * <p>规则由 {@code NacosIntegration} 在启动时加载并通过
 * {@link #updateRuleConfig(String, RuleConfig)} 注入。</p>
 *
 * @author ispengya
 */
public final class RuleManager {

    private static final Logger log = LoggerFactory.getLogger(RuleManager.class);

    /**
     * 默认 duration（秒），当 key 没有匹配到任何规则时使用。
     */
    private static final int DEFAULT_DURATION = 60;

    /**
     * 规则配置缓存：appName → RuleConfig。
     */
    private final ConcurrentMap<String, RuleConfig> ruleConfigs = new ConcurrentHashMap<>();

    /**
     * 默认最小访问次数阈值，当 key 没有匹配到规则时使用。
     */
    private final long defaultMinCountThreshold;

    public RuleManager(long defaultMinCountThreshold) {
        this.defaultMinCountThreshold = defaultMinCountThreshold;
    }

    /**
     * 更新某个应用的规则配置。
     *
     * @param appName    应用名称
     * @param ruleConfig 规则配置
     */
    public void updateRuleConfig(String appName, RuleConfig ruleConfig) {
        if (appName == null || ruleConfig == null) {
            return;
        }
        ruleConfigs.put(appName, ruleConfig);
        log.info("Rule config updated for app: {}, ruleCount: {}", appName, ruleConfig.getRules().size());
    }

    /**
     * 判断给定的 key 是否在规则范围内。
     * 如果 appName 没有配置规则，返回 false（不计算任何 key）。
     *
     * @param appName 应用名称
     * @param key     业务 key
     * @return 在规则范围内返回 true
     */
    public boolean isInRule(String appName, String key) {
        if (appName == null || key == null) {
            return false;
        }
        RuleConfig config = ruleConfigs.get(appName);
        if (config == null) {
            return false;
        }
        return config.isInRule(key);
    }

    /**
     * 获取指定 key 的热 key 判定阈值。
     * 优先使用规则中配置的阈值，其次使用默认阈值。
     *
     * @param appName 应用名称
     * @param key     业务 key
     * @return 阈值
     */
    public long getThreshold(String appName, String key) {
        if (appName == null || key == null) {
            return defaultMinCountThreshold;
        }
        RuleConfig config = ruleConfigs.get(appName);
        if (config == null) {
            return defaultMinCountThreshold;
        }
        KeyRule rule = config.findRule(key);
        if (rule == null) {
            return defaultMinCountThreshold;
        }
        return Math.max(rule.getThreshold(), 1L);
    }

    /**
     * 获取指定 key 的热 key 缓存时长（秒）。
     *
     * @param appName 应用名称
     * @param key     业务 key
     * @return duration（秒）
     */
    public int getDuration(String appName, String key) {
        if (appName == null || key == null) {
            return DEFAULT_DURATION;
        }
        RuleConfig config = ruleConfigs.get(appName);
        if (config == null) {
            return DEFAULT_DURATION;
        }
        KeyRule rule = config.findRule(key);
        if (rule == null) {
            return DEFAULT_DURATION;
        }
        return rule.getDuration() > 0 ? rule.getDuration() : DEFAULT_DURATION;
    }

    /**
     * 获取某个应用的规则配置。
     *
     * @param appName 应用名称
     * @return 规则配置，未配置返回 null
     */
    public RuleConfig getRuleConfig(String appName) {
        return ruleConfigs.get(appName);
    }

    /**
     * 清除所有规则配置（测试用）。
     */
    public void clearAll() {
        ruleConfigs.clear();
        log.info("All rule configs cleared");
    }
}
