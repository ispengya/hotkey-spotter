package com.ispengya.hotkey.cli.cache;

/**
 * 默认 Caffeine 缓存实现（60秒过期）。
 *
 * @author ispengya
 */
public class DefaultCaffeineCache extends CaffeineCache {

    /**
     * 默认过期时长（秒）
     */
    private static final int DEFAULT_DURATION_SECONDS = 60;

    /**
     * 创建默认 Caffeine 缓存（60秒过期）。
     */
    public DefaultCaffeineCache() {
        super(DEFAULT_DURATION_SECONDS);
    }
}
