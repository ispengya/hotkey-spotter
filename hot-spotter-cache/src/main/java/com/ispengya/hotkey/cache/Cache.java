package com.ispengya.hotkey.cache;

import java.util.concurrent.TimeUnit;

/**
 * 缓存接口。
 *
 * <p>定义缓存的基本操作，支持 L1（本地）、L2（Redis）、L3（数据源）三级缓存。</p>
 *
 * @param <K> 缓存 key 类型
 * @param <V> 缓存 value 类型
 * @author ispengya
 */
public interface Cache<K, V> {

    /**
     * 获取缓存名称。
     */
    String getName();

    /**
     * 获取缓存值。
     *
     * @param key 缓存 key
     * @return 缓存值，不存在返回 null
     */
    V get(K key);

    /**
     * 设置缓存值。
     *
     * @param key      缓存 key
     * @param value    缓存值
     * @param duration 过期时间
     * @param unit     时间单位
     */
    void put(K key, V value, long duration, TimeUnit unit);

    /**
     * 设置缓存值（使用默认过期时间）。
     *
     * @param key   缓存 key
     * @param value 缓存值
     */
    void put(K key, V value);

    /**
     * 删除缓存。
     *
     * @param key 缓存 key
     */
    void evict(K key);

    /**
     * 清空缓存。
     */
    void clear();

    /**
     * 判断缓存是否存在。
     *
     * @param key 缓存 key
     * @return 是否存在
     */
    boolean containsKey(K key);

    /**
     * 获取缓存大小。
     */
    long size();
}
