package com.ispengya.hotkey.cli.core;

import com.ispengya.hotkey.cli.cache.HKCache;
import com.ispengya.hotkey.cli.detect.HotKeyDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * HotKeyClient 是对业务方暴露的统一访问入口，
 * 负责串联热 key 探测和本地缓存能力。
 *
 * <p>参考 hotkey 项目的 JdHotKeyStore 设计，热 key 标记直接存储在本地缓存中：
 * <ul>
 *     <li>Worker 推送热 key 时，存入缓存，value 为 {@link #MAGIC_VALUE} 标记</li>
 *     <li>判断热 key：缓存中存在该 key 即为热 key</li>
 *     <li>业务 set：覆盖标记值为真实值</li>
 *     <li>业务 get：如果是标记值返回 null，否则返回真实值</li>
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

    /**
     * 魔法值，用于标记热 key。
     * Worker 推送热 key 时，value 为此值。
     * 业务 get 时，如果返回此值，应视为 null（未设置真实值）。
     */
    public static final Object MAGIC_VALUE = new Object();

    /**
     * 热 key 续期阈值（毫秒）。
     * 当热 key 剩余过期时间小于此值时，触发重新上报。
     */
    private static final long HOT_KEY_RENEW_THRESHOLD_MS = 2000;

    private final HotKeyDetector hotKeyDetector;
    private final HKCache<String, Object> cache;
    private final String appName;

    /**
     * 热 key 过期时间记录（key -> 过期时间戳）。
     */
    private final ConcurrentMap<String, Long> hotKeyExpiryMap = new ConcurrentHashMap<>();

    /**
     * 使用必要依赖构造 HotKeyClient。
     *
     * @param hotKeyDetector 热 key 探测器
     * @param cache          本地缓存
     * @param appName        应用名称
     */
    public HotKeyClient(HotKeyDetector hotKeyDetector,
                        HKCache<String, Object> cache,
                        String appName) {
        this.hotKeyDetector = hotKeyDetector;
        this.cache = cache;
        this.appName = appName;
    }

    /**
     * 判断是否是热 key。
     *
     * <p>通过检查本地缓存中是否存在该 key 来判断。
     * 热 key 由 Worker 推送时存入缓存。</p>
     *
     * @param key 业务 key
     * @return true 如果是热 key
     */
    public boolean isHotKey(String key) {
        return cache.get(key) != null;
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
     * <p>如果缓存中的值是 {@link #MAGIC_VALUE}（标记值），返回 null。</p>
     *
     * @param key 业务 key
     * @return 缓存的值，如果不存在或为标记值返回 null
     */
    public Object get(String key) {
        boolean isHot = isHotKey(key);

        if (!isHot) {
            // 非热 key，记录访问用于上报
            hotKeyDetector.recordAccess(key);
        } else {
            // 热 key，检查是否需要续期
            checkAndRenewHotKey(key);
        }

        Object value = cache.get(key);
        // 如果是标记值，返回 null
        if (value == MAGIC_VALUE) {
            return null;
        }
        return value;
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
        if (isHotKey(key)) {
            cache.put(key, value);
            log.debug("Set cache: key={}", key);
        }
    }

    /**
     * 添加热 key 到本地缓存。
     *
     * <p>当收到 Worker 推送的热 key 时，调用此方法。
     * 存入标记值 {@link #MAGIC_VALUE}，业务 set 时会覆盖为真实值。</p>
     *
     * @param key            热 key
     * @param durationMillis 过期时长（毫秒）
     */
    public void addHotKey(String key, long durationMillis) {
        cache.put(key, MAGIC_VALUE);
        long expiryTime = System.currentTimeMillis() + durationMillis;
        hotKeyExpiryMap.put(key, expiryTime);
        log.debug("Add hot key: key={}, expiryTime={}", key, expiryTime);
    }

    /**
     * 删除热 key。
     *
     * <p>当收到 Worker 推送的热 key 删除事件时，调用此方法。</p>
     *
     * @param key 热 key
     */
    public void removeHotKey(String key) {
        cache.evict(key);
        hotKeyExpiryMap.remove(key);
        log.debug("Remove hot key: key={}", key);
    }

    /**
     * 删除指定 key 的本地缓存。
     *
     * @param key 业务 key
     */
    public void remove(String key) {
        cache.evict(key);
        hotKeyExpiryMap.remove(key);
        log.info("Remove cache: key={}", key);
    }

    /**
     * 清空本地缓存。
     */
    public void clear() {
        cache.clear();
        hotKeyExpiryMap.clear();
        log.info("Clear all cache");
    }

    /**
     * 检查热 key 是否需要续期，如果需要则重新上报。
     *
     * @param key 热 key
     */
    private void checkAndRenewHotKey(String key) {
        Long expiryTime = hotKeyExpiryMap.get(key);
        if (expiryTime == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long ttl = expiryTime - now;

        if (ttl > 0 && ttl < HOT_KEY_RENEW_THRESHOLD_MS) {
            // 热 key 即将过期，重新上报
            log.info("Hot key near expiry, re-reporting: key={}, ttlMs={}", key, ttl);
            hotKeyDetector.recordAccess(key);
            // 移除过期时间记录，等待下次推送更新
            hotKeyExpiryMap.remove(key);
        } else if (ttl <= 0) {
            // 已过期，移除记录
            hotKeyExpiryMap.remove(key);
        }
    }
}
