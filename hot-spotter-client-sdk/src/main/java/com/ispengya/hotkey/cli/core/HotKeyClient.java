package com.ispengya.hotkey.cli.core;

import com.ispengya.hotkey.cli.cache.Cache;
import com.ispengya.hotkey.cli.cache.CacheHolder;
import com.ispengya.hotkey.cli.cache.ValueModel;
import com.ispengya.hotkey.cli.detect.HotKeyDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HotKeyClient 是对业务方暴露的统一访问入口，
 * 负责串联热 key 探测和本地缓存能力。
 *
 * <p>提供简单直接的 API：
 * <ul>
 *     <li>{@link #get(String)} - 从本地缓存获取值</li>
 *     <li>{@link #isHotKey(String)} - 判断是否热 key</li>
 *     <li>{@link #set(String, Object)} - 设置值到本地缓存</li>
 *     <li>{@link #remove(String)} - 删除缓存</li>
 * </ul>
 * </p>
 *
 * <p>典型用法：
 * <pre>{@code
 * Object value = hotKeyClient.get(key);
 * if (value == null) {
 *     // 回源查询
 *     value = db.query(key);
 *     // 设置到本地缓存（只有热 key 才会存入）
 *     hotKeyClient.set(key, value);
 * }
 * }</pre>
 * </p>
 *
 * @author ispengya
 */
public class HotKeyClient {

    private static final Logger log = LoggerFactory.getLogger(HotKeyClient.class);

    private final HotKeyDetector hotKeyDetector;
    private final CacheHolder cacheHolder;

    /**
     * 使用必要依赖构造 HotKeyClient。
     *
     * @param hotKeyDetector 热 key 探测器
     * @param cacheHolder    缓存持有者
     */
    public HotKeyClient(HotKeyDetector hotKeyDetector, CacheHolder cacheHolder) {
        this.hotKeyDetector = hotKeyDetector;
        this.cacheHolder = cacheHolder;
    }

    /**
     * 判断是否是热 key。
     *
     * <p>通过检查本地缓存中是否存在未过期的该 key 来判断。
     * 热 key 由 Worker 推送时存入缓存。</p>
     *
     * @param key 业务 key
     * @return true 如果是热 key 且未过期
     */
    public boolean isHotKey(String key) {
        ValueModel model = cacheHolder.findCache(key).get(key);
        return model != null && !model.isExpired();
    }

    /**
     * 从本地缓存获取值。
     *
     * <p>内部会判断是否热 key，并触发上报逻辑：
     * <ul>
     *     <li>非热 key：记录访问用于上报给 Worker</li>
     *     <li>热 key：检查是否需要续期</li>
     * </ul>
     * </p>
     *
     * @param key 业务 key
     * @return 缓存的值，如果不存在或为标记值返回 null
     */
    public Object get(String key) {
        Cache cache = cacheHolder.findCache(key);
        ValueModel model = cache.get(key);

        // 非热 key 或已过期
        if (model == null) {
            hotKeyDetector.recordAccess(key);
            return null;
        }

        // 已过期，删除并返回 null
        if (model.isExpired()) {
            cache.evict(key);
            hotKeyDetector.recordAccess(key);
            return null;
        }

        // 临近过期，重新上报
        if (model.isNearExpire()) {
            hotKeyDetector.recordAccess(key);
        }

        // 是热 key 标记（未 set 真实值）
        if (model.isMagicValue()) {
            return null;
        }

        return model.getValue();
    }

    /**
     * 设置值到本地缓存。
     *
     * <p>只有当 key 是热 key 时才会存入缓存。
     * 非热 key 不需要缓存，直接回源即可。</p>
     *
     * @param key   业务 key
     * @param value 要缓存的值
     */
    public void set(String key, Object value) {
        Cache cache = cacheHolder.findCache(key);
        ValueModel model = cache.get(key);
        if (model != null && !model.isExpired()) {
            model.setValue(value);
            log.debug("Set cache: key={}", key);
        }
    }

    /**
     * 添加热 key 到本地缓存。
     *
     * <p>当收到 Worker 推送的热 key 时，调用此方法。</p>
     *
     * @param key             热 key
     * @param durationSeconds 过期时长（秒）
     */
    public void addHotKey(String key, int durationSeconds) {
        Cache cache = cacheHolder.getCache(durationSeconds);
        cache.put(key, ValueModel.defaultValue(durationSeconds));
        log.debug("Add hot key: key={}, durationSeconds={}", key, durationSeconds);
    }

    /**
     * 删除热 key。
     *
     * <p>当收到 Worker 推送的热 key 删除事件时，调用此方法。</p>
     *
     * @param key 热 key
     */
    public void removeHotKey(String key) {
        cacheHolder.findCache(key).evict(key);
        log.debug("Remove hot key: key={}", key);
    }

    /**
     * 删除指定 key 的本地缓存。
     *
     * @param key 业务 key
     */
    public void remove(String key) {
        cacheHolder.findCache(key).evict(key);
        log.info("Remove cache: key={}", key);
    }

    /**
     * 清空所有本地缓存。
     */
    public void clear() {
        cacheHolder.clearAll();
        log.info("Clear all cache");
    }

    /**
     * 获取缓存持有者（用于高级操作）。
     *
     * @return 缓存持有者
     */
    public CacheHolder getCacheHolder() {
        return cacheHolder;
    }
}
