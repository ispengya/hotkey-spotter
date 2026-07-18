package com.ispengya.hotkey.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * L1 本地缓存（基于 Caffeine）。
 *
 * <p>高性能本地缓存，适用于热点 key 的快速访问。</p>
 *
 * @param <K> 缓存 key 类型
 * @param <V> 缓存 value 类型
 * @author ispengya
 */
public class CaffeineCache<K, V> implements Cache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(CaffeineCache.class);

    private final String name;
    private final com.github.benmanes.caffeine.cache.Cache<K, V> cache;
    private final long defaultDuration;
    private final TimeUnit defaultTimeUnit;

    public CaffeineCache(String name, long maxSize, long defaultDuration, TimeUnit defaultTimeUnit) {
        this.name = name;
        this.defaultDuration = defaultDuration;
        this.defaultTimeUnit = defaultTimeUnit;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(defaultDuration, defaultTimeUnit)
                .recordStats()
                .build();
        log.info("CaffeineCache initialized: name={}, maxSize={}, defaultDuration={} {}",
                name, maxSize, defaultDuration, defaultTimeUnit);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public V get(K key) {
        V value = cache.getIfPresent(key);
        log.debug("Cache get: name={}, key={}, hit={}", name, key, value != null);
        return value;
    }

    @Override
    public void put(K key, V value, long duration, TimeUnit unit) {
        // Caffeine 不支持单个 key 自定义过期时间，使用全局配置
        cache.put(key, value);
        log.debug("Cache put: name={}, key={}", name, key);
    }

    @Override
    public void put(K key, V value) {
        put(key, value, defaultDuration, defaultTimeUnit);
    }

    @Override
    public void evict(K key) {
        cache.invalidate(key);
        log.debug("Cache evict: name={}, key={}", name, key);
    }

    @Override
    public void clear() {
        cache.invalidateAll();
        log.info("Cache clear: name={}", name);
    }

    @Override
    public boolean containsKey(K key) {
        return cache.getIfPresent(key) != null;
    }

    @Override
    public long size() {
        return cache.estimatedSize();
    }

    /**
     * 获取缓存统计信息。
     */
    public com.github.benmanes.caffeine.cache.stats.CacheStats stats() {
        return cache.stats();
    }
}
