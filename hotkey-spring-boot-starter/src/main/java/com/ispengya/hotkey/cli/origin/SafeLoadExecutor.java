package com.ispengya.hotkey.cli.origin;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SafeLoadExecutor 抽象基于本地锁的安全回源能力，
 * 用于在高并发场景下避免对同一 key 的重复回源。
 *
 * 默认实现由 DefaultSafeLoadExecutor 提供，内部包含 key 维度的锁管理。
 *
 * @author ispengya
 */
public interface SafeLoadExecutor {

    /**
     * 为指定 key 获取或创建一个本地锁，并加锁。
     *
     * @param key 业务 key
     * @return 已加锁的 ReentrantLock
     */
    ReentrantLock acquireLock(String key);

    /**
     * 释放本地锁。
     *
     * @param lock 需要释放的锁对象
     */
    void releaseLock(ReentrantLock lock);

    /**
     * 使用本地锁保护回源逻辑，避免同一 key 的并发击穿。
     *
     * @param key    业务 key
     * @param loader 回源逻辑
     * @param <T>    返回值类型
     * @return 回源结果
     * @throws Exception 当回源逻辑抛出异常时向上透传
     */
    <T> T safeLoad(String key, Callable<T> loader) throws Exception;
}
