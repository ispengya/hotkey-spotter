package com.ispengya.hotkey.cli.cache;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;

/**
 * SimpleLocalCache 基于 Caffeine 提供本地缓存能力，
 * 支持最大容量和写入后过期时间的基础控制。
 *
 * 当前实现使用全局 TTL，不根据每次 put 的 ttlMillis 做细粒度控制。
 *
 * @author ispengya
 */
public class DefaultLocalCache<K, V> implements ICache<K, V> {

    private final Cache<K, V> cache;


    /**
     * 使用指定容量和过期时间构造 Caffeine 缓存。
     *
     * @param maximumSize            最大缓存条目数
     * @param expireAfterWriteMillis 写入后过期时间（毫秒）
     */
    public DefaultLocalCache(long maximumSize, long expireAfterWriteMillis) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(expireAfterWriteMillis, TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * 从 Caffeine 缓存中按 key 获取值。
     *
     * @param key 缓存 key
     * @return 命中时返回对应的值，否则返回 null
     */
    @Override
    public V getIfPresent(K key) {
        return cache.getIfPresent(key);
    }

    /**
     * 将值写入 Caffeine 缓存。
     * 当前实现忽略 ttlMillis，统一使用构造时配置的 TTL。
     *
     * @param key       缓存 key
     * @param value     需要缓存的值
     * @param ttlMillis 过期时间（毫秒），此实现中未使用
     */
    @Override
    public void put(K key, V value, long ttlMillis) {
        cache.put(key, value);
    }

    /**
     * 从 Caffeine 缓存中移除指定 key。
     *
     * @param key 缓存 key
     */
    @Override
    public void invalidate(K key) {
        cache.invalidate(key);
    }

    /**
     * 清空 Caffeine 缓存中的全部条目。
     */
    @Override
    public void invalidateAll() {
        cache.invalidateAll();
    }
}

