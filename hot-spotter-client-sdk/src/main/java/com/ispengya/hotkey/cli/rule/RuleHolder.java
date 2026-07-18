package com.ispengya.hotkey.cli.rule;

import com.ispengya.hotkey.common.rule.KeyRule;
import com.ispengya.hotkey.common.rule.RuleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则持有者，存储和管理热 key 探测规则。
 *
 * <p>参考 hotkey 项目的 KeyRuleHolder 设计，提供：
 * <ul>
 *     <li>根据 key 查找匹配的规则</li>
 *     <li>获取 key 对应的 duration</li>
 *     <li>判断 key 是否在规则范围内</li>
 * </ul>
 * </p>
 *
 * @author ispengya
 */
public class RuleHolder {

    private static final Logger log = LoggerFactory.getLogger(RuleHolder.class);

    /**
     * 规则配置缓存：appName -> RuleConfig
     */
    private static final ConcurrentHashMap<String, RuleConfig> RULE_CONFIG_CACHE = new ConcurrentHashMap<>();

    /**
     * 默认 duration（秒）
     */
    private static final int DEFAULT_DURATION = 60;

    /**
     * 更新规则配置。
     *
     * @param appName    应用名称
     * @param ruleConfig 规则配置
     */
    public static void updateRuleConfig(String appName, RuleConfig ruleConfig) {
        if (appName == null || ruleConfig == null) {
            return;
        }
        RULE_CONFIG_CACHE.put(appName, ruleConfig);
        log.info("Rule config updated for app: {}, rules: {}", appName, ruleConfig.getRules().size());
    }

    /**
     * 获取规则配置。
     *
     * @param appName 应用名称
     * @return 规则配置，如果不存在返回 null
     */
    public static RuleConfig getRuleConfig(String appName) {
        return RULE_CONFIG_CACHE.get(appName);
    }

    /**
     * 根据 key 查找匹配的规则。
     *
     * @param appName 应用名称
     * @param key     业务 key
     * @return 匹配的规则，如果没有匹配返回 null
     */
    public static KeyRule findRule(String appName, String key) {
        RuleConfig ruleConfig = RULE_CONFIG_CACHE.get(appName);
        if (ruleConfig == null) {
            return null;
        }
        return ruleConfig.findRule(key);
    }

    /**
     * 获取 key 对应的 duration（秒）。
     *
     * @param appName 应用名称
     * @param key     业务 key
     * @return duration（秒），如果没有匹配的规则返回默认值 60
     */
    public static int getDuration(String appName, String key) {
        KeyRule rule = findRule(appName, key);
        if (rule == null) {
            return DEFAULT_DURATION;
        }
        return rule.getDuration();
    }

    /**
     * 判断 key 是否在规则范围内。
     *
     * @param appName 应用名称
     * @param key     业务 key
     * @return 在规则范围内返回 true
     */
    public static boolean isInRule(String appName, String key) {
        RuleConfig ruleConfig = RULE_CONFIG_CACHE.get(appName);
        if (ruleConfig == null) {
            return false;
        }
        return ruleConfig.isInRule(key);
    }

    /**
     * 清除所有规则配置（用于测试或重置）。
     */
    public static void clearAll() {
        RULE_CONFIG_CACHE.clear();
        log.info("All rule configs cleared");
    }
}
