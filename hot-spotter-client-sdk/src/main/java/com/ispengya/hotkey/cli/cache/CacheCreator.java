package com.ispengya.hotkey.cli.cache;

/**
 * 缓存创建器接口。
 *
 * <p>业务方可实现此接口来提供自定义缓存实现（如 Redis）。</p>
 *
 * @author ispengya
 */
public interface CacheCreator {

    /**
     * 创建缓存实例。
     *
     * @param durationSeconds 过期时长（秒）
     * @return 缓存实例
     */
    Cache create(int durationSeconds);
}
