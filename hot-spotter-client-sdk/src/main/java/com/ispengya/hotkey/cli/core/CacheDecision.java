package com.ispengya.hotkey.cli.core;

/**
 * CacheDecision 表示缓存模板对当前访问是否使用缓存的决策结果，
 * 并可携带建议的缓存过期时间。
 *
 * 目前仅包含是否启用缓存以及可选的 TTL 配置。
 *
 * @author ispengya
 */
public final class CacheDecision {

    private final boolean useCache;
    private final Long ttlMillis;

    /**
     * 构造缓存决策。
     *
     * @param useCache  是否使用缓存
     * @param ttlMillis 建议写入缓存的过期时间，单位毫秒，允许为 null
     */
    public CacheDecision(boolean useCache, Long ttlMillis) {
        this.useCache = useCache;
        this.ttlMillis = ttlMillis;
    }

    /**
     * 是否使用缓存。
     *
     * @return true 表示使用缓存
     */
    public boolean isUseCache() {
        return useCache;
    }

    /**
     * 获取建议的缓存 TTL。
     *
     * @return 建议 TTL 毫秒值，可能为 null
     */
    public Long getTtlMillis() {
        return ttlMillis;
    }
}

