package com.ispengya.hotkey.cli.core;

import com.ispengya.hotkey.cache.CacheManager;
import com.ispengya.hotkey.cli.origin.SafeLoadExecutor;
import com.ispengya.hotkey.common.exception.HotKeyCacheException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 多级缓存模板实现。
 *
 * <p>集成 {@link CacheManager} 三级缓存框架，支持：
 * <ul>
 *     <li>热 key：使用 L1/L2/L3 多级缓存</li>
 *     <li>非热 key：直接回源，不缓存</li>
 * </ul>
 * </p>
 *
 * @author ispengya
 */
public class MultiLevelCacheTemplate implements CacheTemplate {

    private static final Logger log = LoggerFactory.getLogger(MultiLevelCacheTemplate.class);

    private final CacheManager<String, Object> cacheManager;
    private final SafeLoadExecutor safeLoadExecutor;

    public MultiLevelCacheTemplate(CacheManager<String, Object> cacheManager,
                                   SafeLoadExecutor safeLoadExecutor) {
        this.cacheManager = cacheManager;
        this.safeLoadExecutor = safeLoadExecutor;
    }

    @Override
    public CacheDecision decide(CacheableContext context) {
        if (context.isHot()) {
            return new CacheDecision(true, null);
        }
        return new CacheDecision(false, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> CacheResult<T> load(CacheableContext context, ValueLoader<T> loader) {
        long start = System.nanoTime();
        String key = context.getKey();
        CacheDecision decision = this.decide(context);
        boolean useCache = decision.isUseCache();

        if (useCache) {
            // 从多级缓存获取
            Object cached = cacheManager.get(key);
            if (cached != null) {
                long cost = System.nanoTime() - start;
                log.debug("Cache hit: key={}, costNanos={}", key, cost);
                return new CacheResult<>((T) cached, true, cost);
            }
        }

        try {
            // 回源加载
            T value = safeLoadExecutor.safeLoad(
                    key,
                    () -> loader.load(key)
            );

            if (useCache) {
                // 写入多级缓存
                cacheManager.put(key, value);
                log.debug("Cache put: key={}", key);
            }

            long cost = System.nanoTime() - start;
            return new CacheResult<>(value, false, cost);
        } catch (Exception e) {
            long cost = System.nanoTime() - start;
            throw new HotKeyCacheException("Failed to load value for key: " + key + ", costNanos=" + cost, e);
        }
    }

    @Override
    public void evict(CacheEvictContext context) {
        cacheManager.evict(context.getKey());
        log.debug("Cache evict: key={}", context.getKey());
    }

    @Override
    public void evictAll() {
        cacheManager.clear();
        log.info("Cache evict all");
    }
}
