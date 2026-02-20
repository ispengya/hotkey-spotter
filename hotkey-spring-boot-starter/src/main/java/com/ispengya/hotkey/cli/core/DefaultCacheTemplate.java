package com.ispengya.hotkey.cli.core;

import com.ispengya.hotkey.cli.cache.ICache;
import com.ispengya.hotkey.cli.origin.SafeLoadExecutor;
import com.ispengya.hotkey.common.exception.HotKeyCacheException;

/**
 * DefaultCacheTemplate 提供一个仅基于本地缓存的简单缓存模板实现，
 * 不涉及远端缓存和复杂策略，适用于初期接入和本地测试。
 *
 * 默认决策为：仅对热 key 使用本地缓存，非热 key 直接回源不缓存。
 *
 * @author ispengya
 */
public class DefaultCacheTemplate implements CacheTemplate {

    private final ICache<String, Object> localCache;
    private final SafeLoadExecutor safeLoadExecutor;

    /**
     * 使用本地缓存和 SafeLoadExecutor 构造 DefaultCacheTemplate。
     *
     * @param localCache       本地缓存实现
     * @param safeLoadExecutor 安全回源执行器
     */
    public DefaultCacheTemplate(ICache<String, Object> localCache, SafeLoadExecutor safeLoadExecutor) {
        this.localCache = localCache;
        this.safeLoadExecutor = safeLoadExecutor;
    }

    @Override
    public CacheDecision decide(CacheableContext context) {
        if (context.isHot()) {
            return new CacheDecision(true, null);
        }
        return new CacheDecision(false, null);
    }

    /**
     * 按决策决定是否使用本地缓存，必要时回源并写入缓存。
     *
     * @param context 访问上下文
     * @param loader  业务回源逻辑
     * @param <T>     返回值类型
     * @return 包含结果和元信息的 CacheResult
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> CacheResult<T> load(CacheableContext context, ValueLoader<T> loader) {
        long start = System.nanoTime();
        String key = context.getKey();
        CacheDecision decision = this.decide(context);
        boolean useCache = decision.isUseCache();
        Long ttlMillis = decision.getTtlMillis();

        if (useCache) {
            Object cached = localCache.getIfPresent(key);
            if (cached != null) {
                long cost = System.nanoTime() - start;
                return new CacheResult<>((T) cached, true, cost);
            }
        }

        try {
            T value = safeLoadExecutor.safeLoad(
                    key,
                    () -> loader.load(key)
            );
            if (useCache) {
                long ttl = ttlMillis != null ? ttlMillis : 0L;
                localCache.put(key, value, ttl);
            }
            long cost = System.nanoTime() - start;
            return new CacheResult<>(value, false, cost);
        } catch (Exception e) {
            long cost = System.nanoTime() - start;
            throw new HotKeyCacheException("Failed to load value for key: " + key + ", costNanos=" + cost, e);
        }
    }

    /**
     * 失效指定 key 的本地缓存。
     *
     * @param context 缓存失效上下文
     */
    @Override
    public void evict(CacheEvictContext context) {
        localCache.invalidate(context.getKey());
    }

    @Override
    public void evictAll() {
        localCache.invalidateAll();
    }
}
