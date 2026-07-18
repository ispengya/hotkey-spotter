package com.ispengya.hotkey.cli.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 缓存构建器。
 *
 * <p>提供创建 Caffeine Cache 的静态方法。</p>
 *
 * @author ispengya
 */
public class CaffeineBuilder {

    /**
     * 默认最大缓存条目数
     */
    private static final int DEFAULT_MAX_SIZE = 10000;

    /**
     * 创建 Caffeine Cache。
     *
     * @param durationSeconds 过期时长（秒）
     * @return Caffeine Cache 实例
     */
    public static Cache<String, ValueModel> build(int durationSeconds) {
        return build(DEFAULT_MAX_SIZE, durationSeconds);
    }

    /**
     * 创建 Caffeine Cache。
     *
     * @param maxSize         最大缓存条目数
     * @param durationSeconds 过期时长（秒）
     * @return Caffeine Cache 实例
     */
    public static Cache<String, ValueModel> build(int maxSize, int durationSeconds) {
        return Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(durationSeconds, TimeUnit.SECONDS)
                .recordStats()
                .build();
    }
}
