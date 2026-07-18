package com.ispengya.hotkey.cli.cache;

/**
 * 本地缓存抽象接口。
 *
 * <p>只定义最基础的缓存操作，存储 {@link ValueModel}。
 * Caffeine 是默认实现，业务方可自行实现此接口扩展（如 Redis）。</p>
 *
 * @author ispengya
 */
public interface Cache {

    /**
     * 获取缓存值。
     *
     * @param key 缓存 key
     * @return ValueModel 实例，如果不存在返回 null
     */
    ValueModel get(String key);

    /**
     * 存入缓存。
     *
     * @param key   缓存 key
     * @param model ValueModel 实例
     */
    void put(String key, ValueModel model);

    /**
     * 删除缓存。
     *
     * @param key 缓存 key
     */
    void evict(String key);

    /**
     * 清空缓存。
     */
    void clear();

    /**
     * 判断缓存中是否存在指定 key。
     *
     * @param key 缓存 key
     * @return true 如果存在
     */
    boolean containsKey(String key);

    /**
     * 获取缓存条目数。
     *
     * @return 缓存条目数
     */
    int size();
}
