package com.ispengya.hotkey.cli.core;

import com.ispengya.hotkey.cli.detect.HotKeyDetector;
import com.ispengya.hotkey.cli.detect.HotKeySet;
import com.ispengya.hotkey.common.model.HotKeyModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * HotKeyClient 是对业务方暴露的统一访问入口，
 * 负责串联热 key 探测、本地缓存和回源等能力。
 *
 * <p>业务只需要提供 ValueLoader 回源逻辑，即可通过 get 方法完成访问。</p>
 *
 * <p>核心功能：
 * <ul>
 *     <li>热 key 探测：记录访问，判断是否热 key</li>
 *     <li>多级缓存：热 key 使用 L1/L2/L3 缓存</li>
 *     <li>过期续期：热 key 过期前自动重新上报</li>
 * </ul>
 * </p>
 *
 * @author ispengya
 */
public class HotKeyClient {

    private static final Logger log = LoggerFactory.getLogger(HotKeyClient.class);

    /**
     * 热 key 续期阈值（毫秒）。
     * 当热 key 剩余过期时间小于此值时，触发重新上报。
     */
    private static final long HOT_KEY_RENEW_THRESHOLD_MS = 2000;

    private final HotKeyDetector hotKeyDetector;
    private final HotKeySet hotKeySet;
    private final CacheTemplate cacheTemplate;
    private final PostLoadAction postLoadAction;
    private final String appName;

    /**
     * 热 key 过期时间记录（key -> 过期时间戳）。
     */
    private final ConcurrentMap<String, Long> hotKeyExpiryMap = new ConcurrentHashMap<>();

    /**
     * 使用必要依赖构造 HotKeyClient。
     *
     * @param hotKeyDetector  热 key 探测器
     * @param hotKeySet       本地热 key 视图
     * @param cacheTemplate   缓存模板
     * @param postLoadAction  加载完成后的回调
     * @param appName         应用名称
     */
    public HotKeyClient(HotKeyDetector hotKeyDetector,
                        HotKeySet hotKeySet,
                        CacheTemplate cacheTemplate,
                        PostLoadAction postLoadAction,
                        String appName) {
        this.hotKeyDetector = hotKeyDetector;
        this.hotKeySet = hotKeySet;
        this.cacheTemplate = cacheTemplate;
        this.postLoadAction = postLoadAction;
        this.appName = appName;
    }

    /**
     * 统一访问入口：记录访问、判断是否热 key、走缓存模板并上报结果。
     *
     * <p>核心流程：
     * <ol>
     *     <li>判断 key 是否为热 key</li>
     *     <li>如果不是热 key，记录访问用于上报</li>
     *     <li>如果是热 key，检查是否需要续期</li>
     *     <li>通过缓存模板加载数据（热 key 走多级缓存）</li>
     * </ol>
     * </p>
     *
     * @param key    业务 key
     * @param loader 回源逻辑
     * @param <T>    返回值类型
     * @return 业务数据结果
     */
    public <T> T get(String key, ValueLoader<T> loader) {
        long startNs = System.nanoTime();

        boolean isHot = hotKeySet.contains(key);

        if (!isHot) {
            // 非热 key，记录访问用于上报
            hotKeyDetector.recordAccess(key);
        } else {
            // 热 key，检查是否需要续期
            checkAndRenewHotKey(key);
        }

        CacheableContext context = new CacheableContext(key, isHot, System.currentTimeMillis());

        CacheResult<T> result = cacheTemplate.load(context, loader);

        postLoadAction.onLoaded(key, result.getValue(), result.isFromCache(), result.getCostNanos());

        if (log.isDebugEnabled()) {
            log.debug("Get hot key. key={}, hot={}, fromCache={}, costNanos={}",
                    key, isHot, result.isFromCache(), result.getCostNanos());
        }

        return result.getValue();
    }

    /**
     * 记录热 key 的过期时间。
     *
     * <p>当收到 Worker 推送的热 key 时，调用此方法记录过期时间。</p>
     *
     * @param key           热 key
     * @param durationMillis 过期时长（毫秒）
     */
    public void recordHotKeyExpiry(String key, long durationMillis) {
        long expiryTime = System.currentTimeMillis() + durationMillis;
        hotKeyExpiryMap.put(key, expiryTime);
        log.debug("Record hot key expiry: key={}, expiryTime={}", key, expiryTime);
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

    /**
     * 失效当前实例下指定 key 的本地缓存。
     *
     * @param key 需要失效的业务 key
     */
    public void evict(String key) {
        CacheEvictContext context = new CacheEvictContext(
                key,
                System.currentTimeMillis()
        );
        cacheTemplate.evict(context);
        hotKeyExpiryMap.remove(key);
        if (log.isInfoEnabled()) {
            log.info("Evict local cache. key={}", key);
        }
    }

    public void evictAll() {
        cacheTemplate.evictAll();
        hotKeyExpiryMap.clear();
        if (log.isInfoEnabled()) {
            log.info("Evict all local cache.");
        }
    }
}
