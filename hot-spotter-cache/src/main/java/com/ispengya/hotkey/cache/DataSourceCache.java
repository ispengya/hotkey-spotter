package com.ispengya.hotkey.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * L3 数据源缓存。
 *
 * <p>从数据源（如数据库、nacos 配置）加载数据的缓存。</p>
 *
 * <p>当 L1 和 L2 缓存都未命中时，从数据源加载数据。</p>
 *
 * @param <K> 缓存 key 类型
 * @param <V> 缓存 value 类型
 * @author ispengya
 */
public class DataSourceCache<K, V> implements Cache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(DataSourceCache.class);

    private final String name;
    private final DataSource<K, V> dataSource;
    private final long defaultDuration;
    private final TimeUnit defaultTimeUnit;

    public DataSourceCache(String name, DataSource<K, V> dataSource, long defaultDuration, TimeUnit defaultTimeUnit) {
        this.name = name;
        this.dataSource = dataSource;
        this.defaultDuration = defaultDuration;
        this.defaultTimeUnit = defaultTimeUnit;
        log.info("DataSourceCache initialized: name={}", name);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public V get(K key) {
        try {
            V value = dataSource.load(key);
            log.debug("DataSource get: name={}, key={}, hit={}", name, key, value != null);
            return value;
        } catch (Exception e) {
            log.error("DataSource get error: name={}, key={}", name, key, e);
            return null;
        }
    }

    @Override
    public void put(K key, V value, long duration, TimeUnit unit) {
        try {
            dataSource.save(key, value);
            log.debug("DataSource put: name={}, key={}", name, key);
        } catch (Exception e) {
            log.error("DataSource put error: name={}, key={}", name, key, e);
        }
    }

    @Override
    public void put(K key, V value) {
        put(key, value, defaultDuration, defaultTimeUnit);
    }

    @Override
    public void evict(K key) {
        try {
            dataSource.delete(key);
            log.debug("DataSource evict: name={}, key={}", name, key);
        } catch (Exception e) {
            log.error("DataSource evict error: name={}, key={}", name, key, e);
        }
    }

    @Override
    public void clear() {
        try {
            dataSource.clear();
            log.info("DataSource clear: name={}", name);
        } catch (Exception e) {
            log.error("DataSource clear error: name={}", name, e);
        }
    }

    @Override
    public boolean containsKey(K key) {
        try {
            return dataSource.load(key) != null;
        } catch (Exception e) {
            log.error("DataSource containsKey error: name={}, key={}", name, key, e);
            return false;
        }
    }

    @Override
    public long size() {
        return -1;
    }

    /**
     * 数据源接口。
     *
     * <p>需要实现此接口以对接具体的数据源（如数据库、nacos 配置）。</p>
     */
    public interface DataSource<K, V> {
        V load(K key);
        void save(K key, V value);
        void delete(K key);
        void clear();
    }
}
