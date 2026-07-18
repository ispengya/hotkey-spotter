package com.ispengya.hotkey.cli.cache;

import java.util.concurrent.TimeUnit;

/**
 * 本地缓存抽象接口。
 *
 * 只定义最基础的缓存操作，不包含多级缓存、回源等复杂逻辑。
 * Caffeine 是默认实现，业务方可自行实现此接口扩展（如 Redis）。
 *
 * @author ispengya
 */
public interface HKCache<K, V> {

    V get(K key);

    void put(K key, V value);

    void put(K key, V value, long duration, TimeUnit timeUnit);

    void evict(K key);

    void clear();

    boolean containsKey(K key);

    int size();
}
