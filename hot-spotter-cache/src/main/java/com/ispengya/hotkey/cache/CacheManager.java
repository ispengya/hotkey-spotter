package com.ispengya.hotkey.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 缓存管理器。
 *
 * <p>管理三级缓存（L1 Caffeine、L2 Redis、L3 数据源），提供统一的缓存访问接口。</p>
 *
 * <p>缓存查询顺序：L1 → L2 → L3，命中后回填到上一级缓存。</p>
 *
 * @param <K> 缓存 key 类型
 * @param <V> 缓存 value 类型
 * @author ispengya
 */
public class CacheManager<K, V> {

    private static final Logger log = LoggerFactory.getLogger(CacheManager.class);

    private final String name;
    private final CacheConfig config;
    private final ConcurrentMap<String, Cache<K, V>> caches = new ConcurrentHashMap<>();

    private Cache<K, V> l1Cache;
    private Cache<K, V> l2Cache;
    private Cache<K, V> l3Cache;

    public CacheManager(String name, CacheConfig config) {
        this.name = name;
        this.config = config;
        log.info("CacheManager created: name={}, config={}", name, config);
    }

    /**
     * 初始化 L1 缓存。
     */
    public void initL1Cache() {
        if (config.isL1Enabled()) {
            l1Cache = new CaffeineCache<>(name + "-L1", config.getL1MaxSize(),
                    config.getL1Duration(), config.getL1TimeUnit());
            caches.put("L1", l1Cache);
            log.info("L1 cache initialized: {}", l1Cache.getName());
        }
    }

    /**
     * 初始化 L2 缓存。
     */
    public void initL2Cache(RedisCache.RedisClient redisClient) {
        if (config.isL2Enabled() && redisClient != null) {
            l2Cache = new RedisCache<>(name + "-L2", redisClient,
                    config.getL2Duration(), config.getL2TimeUnit());
            caches.put("L2", l2Cache);
            log.info("L2 cache initialized: {}", l2Cache.getName());
        }
    }

    /**
     * 初始化 L3 缓存。
     */
    public void initL3Cache(DataSourceCache.DataSource<K, V> dataSource) {
        if (config.isL3Enabled() && dataSource != null) {
            l3Cache = new DataSourceCache<>(name + "-L3", dataSource,
                    config.getL3Duration(), config.getL3TimeUnit());
            caches.put("L3", l3Cache);
            log.info("L3 cache initialized: {}", l3Cache.getName());
        }
    }

    /**
     * 获取缓存值。
     *
     * <p>查询顺序：L1 → L2 → L3，命中后回填到上一级缓存。</p>
     *
     * @param key 缓存 key
     * @return 缓存值，不存在返回 null
     */
    public V get(K key) {
        // L1
        if (l1Cache != null) {
            V value = l1Cache.get(key);
            if (value != null) {
                log.debug("Cache hit L1: key={}", key);
                return value;
            }
        }

        // L2
        if (l2Cache != null) {
            V value = l2Cache.get(key);
            if (value != null) {
                log.debug("Cache hit L2: key={}", key);
                // 回填 L1
                if (l1Cache != null) {
                    l1Cache.put(key, value);
                }
                return value;
            }
        }

        // L3
        if (l3Cache != null) {
            V value = l3Cache.get(key);
            if (value != null) {
                log.debug("Cache hit L3: key={}", key);
                // 回填 L1 和 L2
                if (l1Cache != null) {
                    l1Cache.put(key, value);
                }
                if (l2Cache != null) {
                    l2Cache.put(key, value);
                }
                return value;
            }
        }

        log.debug("Cache miss all levels: key={}", key);
        return null;
    }

    /**
     * 设置缓存值。
     *
     * <p>同时写入所有已启用的缓存层级。</p>
     *
     * @param key   缓存 key
     * @param value 缓存值
     */
    public void put(K key, V value) {
        if (l1Cache != null) {
            l1Cache.put(key, value);
        }
        if (l2Cache != null) {
            l2Cache.put(key, value);
        }
        if (l3Cache != null) {
            l3Cache.put(key, value);
        }
        log.debug("Cache put all levels: key={}", key);
    }

    /**
     * 删除缓存。
     *
     * <p>同时删除所有已启用的缓存层级。</p>
     *
     * @param key 缓存 key
     */
    public void evict(K key) {
        if (l1Cache != null) {
            l1Cache.evict(key);
        }
        if (l2Cache != null) {
            l2Cache.evict(key);
        }
        if (l3Cache != null) {
            l3Cache.evict(key);
        }
        log.debug("Cache evict all levels: key={}", key);
    }

    /**
     * 清空缓存。
     */
    public void clear() {
        if (l1Cache != null) {
            l1Cache.clear();
        }
        if (l2Cache != null) {
            l2Cache.clear();
        }
        if (l3Cache != null) {
            l3Cache.clear();
        }
        log.info("Cache clear all levels: name={}", name);
    }

    /**
     * 获取缓存名称。
     */
    public String getName() {
        return name;
    }

    /**
     * 获取缓存配置。
     */
    public CacheConfig getConfig() {
        return config;
    }

    /**
     * 获取指定层级的缓存。
     */
    public Cache<K, V> getCache(String level) {
        return caches.get(level);
    }

    /**
     * 判断 L1 缓存是否已初始化。
     */
    public boolean hasL1Cache() {
        return l1Cache != null;
    }

    /**
     * 判断 L2 缓存是否已初始化。
     */
    public boolean hasL2Cache() {
        return l2Cache != null;
    }

    /**
     * 判断 L3 缓存是否已初始化。
     */
    public boolean hasL3Cache() {
        return l3Cache != null;
    }
}
