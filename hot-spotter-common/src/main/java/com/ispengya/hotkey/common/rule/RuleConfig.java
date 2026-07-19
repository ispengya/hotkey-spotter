package com.ispengya.hotkey.common.rule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    // ==================== 解析 ====================

    private static final Logger log = LoggerFactory.getLogger(RuleConfig.class);
    private static final Yaml YAML = new Yaml();

    /**
     * 解析规则配置字符串，支持 YAML 和 JSON（JSON 是 YAML 1.2 子集，SnakeYAML 可直接解析）。
     *
     * <p>支持两种格式：</p>
     * <ul>
     *     <li>包裹格式：{@code hotkey: { rules: [...] }} 或 {@code {"hotkey":{"rules":[...]}}}</li>
     *     <li>裸数组：{@code [{key:..., ...}, ...]} 或 {@code [{"key":..., ...}, ...]}</li>
     * </ul>
     *
     * @param appName 应用名称
     * @param config  规则配置文本（YAML 或 JSON）
     * @return 解析后的 RuleConfig，解析失败返回空规则
     */
    public static RuleConfig parse(String appName, String config) {
        if (config == null || config.trim().isEmpty()) {
            return new RuleConfig(appName, new ArrayList<>());
        }

        try {
            Object parsed = YAML.load(config);

            if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> root = (Map<String, Object>) parsed;
                if (root.containsKey("hotkey")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> hotkey = (Map<String, Object>) root.get("hotkey");
                    if (hotkey != null && hotkey.containsKey("rules")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> ruleMaps = (List<Map<String, Object>>) hotkey.get("rules");
                        return new RuleConfig(appName, convertRules(ruleMaps));
                    }
                }
            }

            if (parsed instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> ruleMaps = (List<Map<String, Object>>) parsed;
                return new RuleConfig(appName, convertRules(ruleMaps));
            }

            return new RuleConfig(appName, new ArrayList<>());
        } catch (Exception e) {
            log.error("Failed to parse rule config. appName={}", appName, e);
            return new RuleConfig(appName, new ArrayList<>());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<KeyRule> convertRules(List<Map<String, Object>> ruleMaps) {
        List<KeyRule> rules = new ArrayList<>();
        if (ruleMaps == null) {
            return rules;
        }
        for (Object item : ruleMaps) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> map = (Map<String, Object>) item;
            KeyRule rule = new KeyRule();
            rule.setKey((String) map.get("key"));
            rule.setPrefix(Boolean.TRUE.equals(map.get("prefix")));
            rule.setInterval(toInt(map.get("interval"), 60));
            rule.setThreshold(toInt(map.get("threshold"), 100));
            rule.setDuration(toInt(map.get("duration"), 120));
            rule.setDesc((String) map.get("desc"));
            rules.add(rule);
        }
        return rules;
    }

    private static int toInt(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
