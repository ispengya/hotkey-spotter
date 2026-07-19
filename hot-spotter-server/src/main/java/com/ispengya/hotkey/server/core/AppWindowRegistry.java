package com.ispengya.hotkey.server.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ispengya.hotkey.common.rule.KeyRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * AppWindowRegistry 按 appName 维度管理每个 key 的独立 {@link SlidingWindow}。
 *
 * <p>每个 app 对应一个 Caffeine 缓存，key 为业务 key，value 为 {@link SlidingWindow}。
 * 窗口参数（duration、threshold）来自该 key 匹配的 {@link KeyRule}，
 * 未匹配规则则使用默认值。</p>
 *
 * <p>Caffeine 自动淘汰长时间不活跃的 key，避免内存泄漏。</p>
 *
 * @author ispengya
 */
public final class AppWindowRegistry {

    private static final Logger log = LoggerFactory.getLogger(AppWindowRegistry.class);

    /**
     * 默认窗口时长（秒），用于未匹配到规则的 key。
     */
    static final int DEFAULT_DURATION_SECONDS = 60;

    /**
     * 缓存最大条目数（每个 app）。
     */
    private static final long MAX_CACHE_SIZE = 100_000;

    /**
     * 不活跃 key 过期时间。
     */
    private static final Duration EXPIRE_AFTER_ACCESS = Duration.ofMinutes(5);

    /**
     * appName → Caffeine&lt;key, SlidingWindow&gt;
     */
    private final ConcurrentMap<String, Cache<String, SlidingWindow>> appCaches = new ConcurrentHashMap<>();

    private final RuleManager ruleManager;

    public AppWindowRegistry(RuleManager ruleManager) {
        this.ruleManager = ruleManager;
    }

    /**
     * 获取或创建指定 key 的滑动窗口。窗口参数来自规则匹配。
     *
     * @param appName 应用名
     * @param key     业务 key
     * @return 该 key 对应的滑动窗口
     */
    public SlidingWindow getOrCreateWindow(String appName, String key) {
        Cache<String, SlidingWindow> cache = appCaches.computeIfAbsent(appName, this::createAppCache);
        return cache.get(key, k -> createWindowFor(appName, k));
    }

    /**
     * 弹出一个 key 的窗口（热 key 判定后移除，停止继续计数）。
     */
    public void removeWindow(String appName, String key) {
        Cache<String, SlidingWindow> cache = appCaches.get(appName);
        if (cache != null) {
            cache.invalidate(key);
        }
    }

    /**
     * 清空指定 app 的所有窗口缓存。规则变更时调用，让窗口按新规则重建。
     */
    public void clearApp(String appName) {
        Cache<String, SlidingWindow> cache = appCaches.get(appName);
        if (cache != null) {
            cache.invalidateAll();
            log.info("Cleared all windows for app: {}", appName);
        }
    }

    private Cache<String, SlidingWindow> createAppCache(String appName) {
        log.info("Creating window cache for app: {}", appName);
        return Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_SIZE)
                .expireAfterAccess(EXPIRE_AFTER_ACCESS)
                .build();
    }

    private SlidingWindow createWindowFor(String appName, String key) {
        int duration = ruleManager.getDuration(appName, key);
        long threshold = ruleManager.getThreshold(appName, key);
        // 至少允许 2 次，避免噪音
        int thresholdInt = (int) Math.max(threshold, 2L);
        return new SlidingWindow(duration, thresholdInt);
    }
}
