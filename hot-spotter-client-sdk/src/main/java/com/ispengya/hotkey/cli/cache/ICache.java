package com.ispengya.hotkey.cli.cache;

/**
 * Cache 抽象本地缓存行为，用于在单机内存中存储热点数据。
 *
 * 具体实现可以基于 ConcurrentHashMap、Caffeine、Redis或其他缓存库。
 *
 * @author ispengya
 */
public interface ICache<K, V> {

    /**
     * 从缓存中按 key 获取值。
     *
     * @param key 缓存 key
     * @return 命中时返回对应的值，否则返回 null
     */
    V getIfPresent(K key);

    /**
     * 将值写入缓存。
     *
     * @param key       缓存 key
     * @param value     需要缓存的值
     * @param ttlMillis 过期时间（毫秒），实现可以选择忽略
     */
    void put(K key, V value, long ttlMillis);

    /**
     * 删除指定 key 的缓存数据。
     *
     * @param key 缓存 key
     */
    void invalidate(K key);

    /**
     * 清空缓存中的全部数据。
     */
    void invalidateAll();
}

