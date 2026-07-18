package com.ispengya.hotkey.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * L2 Redis 缓存（可选）。
 *
 * <p>基于 Redis 的分布式缓存，适用于多实例间共享缓存。</p>
 *
 * <p>注意：此实现为简化版本，实际使用时需要注入 Redis 客户端（如 Lettuce、Jedis）。</p>
 *
 * @param <K> 缓存 key 类型
 * @param <V> 缓存 value 类型
 * @author ispengya
 */
public class RedisCache<K, V> implements Cache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(RedisCache.class);

    private final String name;
    private final RedisClient redisClient;
    private final long defaultDuration;
    private final TimeUnit defaultTimeUnit;

    public RedisCache(String name, RedisClient redisClient, long defaultDuration, TimeUnit defaultTimeUnit) {
        this.name = name;
        this.redisClient = redisClient;
        this.defaultDuration = defaultDuration;
        this.defaultTimeUnit = defaultTimeUnit;
        log.info("RedisCache initialized: name={}, defaultDuration={} {}", name, defaultDuration, defaultTimeUnit);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(K key) {
        try {
            Object value = redisClient.get(key.toString());
            log.debug("Redis get: name={}, key={}, hit={}", name, key, value != null);
            return (V) value;
        } catch (Exception e) {
            log.error("Redis get error: name={}, key={}", name, key, e);
            return null;
        }
    }

    @Override
    public void put(K key, V value, long duration, TimeUnit unit) {
        try {
            redisClient.set(key.toString(), value, duration, unit);
            log.debug("Redis put: name={}, key={}", name, key);
        } catch (Exception e) {
            log.error("Redis put error: name={}, key={}", name, key, e);
        }
    }

    @Override
    public void put(K key, V value) {
        put(key, value, defaultDuration, defaultTimeUnit);
    }

    @Override
    public void evict(K key) {
        try {
            redisClient.del(key.toString());
            log.debug("Redis evict: name={}, key={}", name, key);
        } catch (Exception e) {
            log.error("Redis evict error: name={}, key={}", name, key, e);
        }
    }

    @Override
    public void clear() {
        try {
            redisClient.clear(name + ":*");
            log.info("Redis clear: name={}", name);
        } catch (Exception e) {
            log.error("Redis clear error: name={}", name, e);
        }
    }

    @Override
    public boolean containsKey(K key) {
        try {
            return redisClient.exists(key.toString());
        } catch (Exception e) {
            log.error("Redis containsKey error: name={}, key={}", name, key, e);
            return false;
        }
    }

    @Override
    public long size() {
        // Redis 不直接支持获取匹配 key 的数量
        return -1;
    }

    /**
     * Redis 客户端接口。
     *
     * <p>需要实现此接口以对接具体的 Redis 客户端（如 Lettuce、Jedis）。</p>
     */
    public interface RedisClient {
        Object get(String key);
        void set(String key, Object value, long duration, TimeUnit unit);
        void del(String key);
        boolean exists(String key);
        void clear(String pattern);
    }
}
