package com.ispengya.hotkey.cli.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Caffeine 的本地缓存实现。
 *
 * 热 key 存储在 Caffeine 中，Caffeine 会自动处理过期和刷新。
 * 业务方如果需要多级缓存（如 L1 本地 + L2 Redis），自行实现 HKCache 接口即可。
 *
 * @author ispengya
 */
public class HKCaffeineCache<K, V> implements HKCache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(HKCaffeineCache.class);

    private final Cache<K, V> cache;
    private final String name;

    /**
     * 创建 Caffeine 缓存。
     *
     * @param name          缓存名称
     * @param maxSize       最大缓存条目数
     * @param duration      过期时长
     * @param timeUnit      过期时长单位
     */
    public HKCaffeineCache(String name, long maxSize, long duration, TimeUnit timeUnit) {
        this.name = name;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(duration, timeUnit)
                .recordStats()
                .build();
        log.info("HKCaffeineCache[{}] initialized, maxSize={}, duration={} {}", name, maxSize, duration, timeUnit);
    }

    /**
     * 使用默认配置创建：10000条目，5分钟过期
     */
    public HKCaffeineCache(String name) {
        this(name, 10000, 5, TimeUnit.MINUTES);
    }

    @Override
    public V get(K key) {
        return cache.getIfPresent(key);
    }

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
    }

    @Override
    public void put(K key, V value, long duration, TimeUnit timeUnit) {
        // Caffeine 使用全局 expireAfterWrite，不支持单条目独立 TTL
        // 如需单条目 TTL，可使用 expireAfter(new Expiry<K, V>() {...})
        cache.put(key, value);
    }

    @Override
    public void evict(K key) {
        cache.invalidate(key);
    }

    @Override
    public void clear() {
        cache.invalidateAll();
    }

    @Override
    public boolean containsKey(K key) {
        return cache.getIfPresent(key) != null;
    }

    @Override
    public int size() {
        return (int) cache.estimatedSize();
    }
}
