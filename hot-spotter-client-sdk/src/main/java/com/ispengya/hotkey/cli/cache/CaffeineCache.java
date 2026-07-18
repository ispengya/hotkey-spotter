package com.ispengya.hotkey.cli.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Caffeine 的本地缓存实现。
 *
 * <p>按 duration 分组，每个实例对应一个过期时间。</p>
 *
 * @author ispengya
 */
public class CaffeineCache implements Cache {

    private static final Logger log = LoggerFactory.getLogger(CaffeineCache.class);

    private final com.github.benmanes.caffeine.cache.Cache<String, ValueModel> cache;
    private final int durationSeconds;

    /**
     * 创建 Caffeine 缓存。
     *
     * @param durationSeconds 过期时长（秒）
     */
    public CaffeineCache(int durationSeconds) {
        this.durationSeconds = durationSeconds;
        this.cache = CaffeineBuilder.build(durationSeconds);
        log.info("CaffeineCache initialized, durationSeconds={}", durationSeconds);
    }

    @Override
    public ValueModel get(String key) {
        return cache.getIfPresent(key);
    }

    @Override
    public void put(String key, ValueModel model) {
        cache.put(key, model);
    }

    @Override
    public void evict(String key) {
        cache.invalidate(key);
    }

    @Override
    public void clear() {
        cache.invalidateAll();
    }

    @Override
    public boolean containsKey(String key) {
        return cache.getIfPresent(key) != null;
    }

    @Override
    public int size() {
        return (int) cache.estimatedSize();
    }

    /**
     * 获取过期时长（秒）。
     *
     * @return 过期时长
     */
    public int getDurationSeconds() {
        return durationSeconds;
    }
}
