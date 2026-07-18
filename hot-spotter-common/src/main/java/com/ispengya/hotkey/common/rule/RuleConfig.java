package com.ispengya.hotkey.common.rule;

import java.util.ArrayList;
import java.util.List;

/**
 * RuleConfig 包装某个应用的所有热 key 探测规则。
 *
 * <p>对应 nacos 配置中心的一个配置文件，格式示例：</p>
 * <pre>
 * hotkey:
 *   rules:
 *     - key: "skuId_*"
 *       prefix: true
 *       interval: 2
 *       threshold: 10
 *       duration: 60
 *     - key: "pin_*"
 *       prefix: true
 *       interval: 5
 *       threshold: 20
 *       duration: 30
 * </pre>
 *
 * @author ispengya
 */
public class RuleConfig {

    /**
     * 应用名称。
     */
    private String appName;

    /**
     * 规则列表。
     */
    private List<KeyRule> rules;

    public RuleConfig() {
        this.rules = new ArrayList<>();
    }

    public RuleConfig(String appName, List<KeyRule> rules) {
        this.appName = appName;
        this.rules = rules != null ? rules : new ArrayList<>();
    }

    /**
     * 根据 key 查找匹配的规则。
     * 优先返回精确匹配的规则，其次返回前缀匹配的规则。
     *
     * @param targetKey 待匹配的 key
     * @return 匹配的规则，如果没有匹配返回 null
     */
    public KeyRule findRule(String targetKey) {
        if (targetKey == null || rules == null) {
            return null;
        }

        // 优先精确匹配
        for (KeyRule rule : rules) {
            if (!rule.isPrefix() && rule.match(targetKey)) {
                return rule;
            }
        }

        // 其次前缀匹配
        for (KeyRule rule : rules) {
            if (rule.isPrefix() && rule.match(targetKey)) {
                return rule;
            }
        }

        return null;
    }

    /**
     * 判断给定的 key 是否在规则范围内。
     *
     * @param targetKey 待判断的 key
     * @return 在规则范围内返回 true
     */
    public boolean isInRule(String targetKey) {
        return findRule(targetKey) != null;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public List<KeyRule> getRules() {
        return rules;
    }

    public void setRules(List<KeyRule> rules) {
        this.rules = rules;
    }

    @Override
    public String toString() {
        return "RuleConfig{" +
                "appName='" + appName + '\'' +
                ", rules=" + rules +
                '}';
    }
}
