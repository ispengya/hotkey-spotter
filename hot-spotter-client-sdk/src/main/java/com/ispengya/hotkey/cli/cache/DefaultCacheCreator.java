package com.ispengya.hotkey.cli.cache;

/**
 * 默认缓存创建器，使用 Caffeine。
 *
 * @author ispengya
 */
public class DefaultCacheCreator implements CacheCreator {

    @Override
    public Cache create(int durationSeconds) {
        return new CaffeineCache(durationSeconds);
    }
}
