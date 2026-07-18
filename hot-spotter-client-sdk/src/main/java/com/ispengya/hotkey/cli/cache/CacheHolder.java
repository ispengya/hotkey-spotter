package com.ispengya.hotkey.cli.cache;

import com.ispengya.hotkey.cli.rule.RuleHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存持有者，管理不同 duration 的缓存实例。
 *
 * <p>按 duration 分组，相同过期时间的 key 共用一个缓存实例。
 * 根据 key 自动查找对应的缓存实例。</p>
 *
 * @author ispengya
 */
public class CacheHolder {

    private static final Logger log = LoggerFactory.getLogger(CacheHolder.class);

    /**
     * 应用名称
     */
    private final String appName;

    /**
     * 按 duration 分组的缓存实例
     */
    private final ConcurrentHashMap<Integer, Cache> cacheMap;

    /**
     * 缓存创建器
     */
    private CacheCreator cacheCreator;

    /**
     * 创建 CacheHolder 实例。
     *
     * @param appName 应用名称
     */
    public CacheHolder(String appName) {
        this.appName = appName;
        this.cacheMap = new ConcurrentHashMap<>();
        this.cacheCreator = new DefaultCacheCreator();
        log.info("CacheHolder initialized for app: {}", appName);
    }

    /**
     * 设置自定义缓存创建器。
     *
     * @param creator 缓存创建器
     */
    public void setCacheCreator(CacheCreator creator) {
        if (creator != null) {
            this.cacheCreator = creator;
            log.info("CacheCreator set to: {}", creator.getClass().getName());
        }
    }

    /**
     * 根据 key 找到对应的缓存实例。
     *
     * <p>通过规则获取 duration，然后返回对应的缓存。
     * 如果没有匹配的规则，使用默认 60 秒。</p>
     *
     * @param key 业务 key
     * @return 缓存实例
     */
    public Cache findCache(String key) {
        int durationSeconds = RuleHolder.getDuration(appName, key);
        return cacheMap.computeIfAbsent(durationSeconds,
            d -> cacheCreator.create(d));
    }

    /**
     * 根据 duration 获取缓存实例。
     *
     * @param durationSeconds 过期时长（秒）
     * @return 缓存实例
     */
    public Cache getCache(int durationSeconds) {
        return cacheMap.computeIfAbsent(durationSeconds,
            d -> cacheCreator.create(d));
    }

    /**
     * 清除所有缓存（用于测试或重置）。
     */
    public void clearAll() {
        cacheMap.values().forEach(Cache::clear);
        cacheMap.clear();
        log.info("All caches cleared for app: {}", appName);
    }

    /**
     * 获取应用名称。
     *
     * @return 应用名称
     */
    public String getAppName() {
        return appName;
    }
}
