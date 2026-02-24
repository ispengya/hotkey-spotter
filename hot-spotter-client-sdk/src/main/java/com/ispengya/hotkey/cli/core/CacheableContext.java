package com.ispengya.hotkey.cli.core;

/**
 * CacheableContext 描述一次访问请求的上下文信息，
 * 用于缓存策略和模板在决策是否使用缓存、如何使用缓存时参考。
 *
 * 包含访问的 key、是否为热 key、当前时间等基础信息。
 *
 * @author ispengya
 */
public final class CacheableContext {

    private final String key;
    private final boolean hot;
    private final long nowMillis;

    /**
     * 构造访问上下文。
     *
     * @param key       当前访问的业务 key
     * @param hot       是否被判定为热 key
     * @param nowMillis 当前访问发生的时间戳（毫秒）
     */
    public CacheableContext(String key, boolean hot, long nowMillis) {
        this.key = key;
        this.hot = hot;
        this.nowMillis = nowMillis;
    }

    /**
     * 获取当前访问的 key。
     *
     * @return 业务 key
     */
    public String getKey() {
        return key;
    }

    /**
     * 判断当前 key 是否为热 key。
     *
     * @return true 表示热 key，false 表示普通 key
     */
    public boolean isHot() {
        return hot;
    }

    /**
     * 获取访问发生的时间戳。
     *
     * @return 毫秒级时间戳
     */
    public long getNowMillis() {
        return nowMillis;
    }
}
