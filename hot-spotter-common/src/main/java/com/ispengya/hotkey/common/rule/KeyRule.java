package com.ispengya.hotkey.common.rule;

/**
 * KeyRule 定义单条热 key 探测规则。
 *
 * <p>规则通过 key 字段匹配业务 key，支持前缀匹配和精确匹配两种模式。
 * 当匹配到规则后，根据 interval（时间窗口秒数）和 threshold（阈值次数）
 * 判定是否为热 key。</p>
 *
 * @author ispengya
 */
public class KeyRule {

    /**
     * 规则匹配的 key 模式。
     * <ul>
     *   <li>精确匹配: "skuId_12345"</li>
     *   <li>前缀匹配: "skuId_*"（配合 prefix=true）</li>
     * </ul>
     */
    private String key;

    /**
     * 是否前缀匹配。
     * <ul>
     *   <li>true: key 以 "*" 结尾，匹配所有以该前缀开头的 key</li>
     *   <li>false: 精确匹配</li>
     * </ul>
     */
    private boolean prefix;

    /**
     * 时间窗口大小（秒）。
     * 在该时间窗口内，访问次数达到 threshold 则判定为热 key。
     */
    private int interval;

    /**
     * 访问次数阈值。
     * 在 interval 时间窗口内，访问次数 >= threshold 则判定为热 key。
     */
    private int threshold;

    /**
     * 热 key 缓存时长（秒）。
     * 热 key 被判定后，客户端本地缓存的过期时间。
     */
    private int duration;

    /**
     * 规则描述（可选）。
     */
    private String desc;

    public KeyRule() {
    }

    public KeyRule(String key, boolean prefix, int interval, int threshold, int duration) {
        this.key = key;
        this.prefix = prefix;
        this.interval = interval;
        this.threshold = threshold;
        this.duration = duration;
    }

    /**
     * 判断给定的 key 是否匹配该规则。
     *
     * @param targetKey 待匹配的 key
     * @return 匹配返回 true
     */
    public boolean match(String targetKey) {
        if (targetKey == null || key == null) {
            return false;
        }
        if (prefix) {
            // 前缀匹配：key 以 "*" 结尾，去掉 "*" 后判断前缀
            String prefixStr = key.endsWith("*") ? key.substring(0, key.length() - 1) : key;
            return targetKey.startsWith(prefixStr);
        } else {
            // 精确匹配
            return key.equals(targetKey);
        }
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public boolean isPrefix() {
        return prefix;
    }

    public void setPrefix(boolean prefix) {
        this.prefix = prefix;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    @Override
    public String toString() {
        return "KeyRule{" +
                "key='" + key + '\'' +
                ", prefix=" + prefix +
                ", interval=" + interval +
                ", threshold=" + threshold +
                ", duration=" + duration +
                ", desc='" + desc + '\'' +
                '}';
    }
}
