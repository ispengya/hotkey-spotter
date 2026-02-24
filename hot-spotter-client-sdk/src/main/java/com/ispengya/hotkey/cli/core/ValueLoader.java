package com.ispengya.hotkey.cli.core;

/**
 * ValueLoader 定义业务方的回源能力，由业务实现具体的数据加载逻辑。
 * 每次 HotKeyClient 未命中缓存时，会通过该接口从后端系统加载数据。
 *
 * @author ispengya
 */
public interface ValueLoader<T> {

    /**
     * 根据给定的 key 从后端系统加载数据。
     *
     * @param key 业务唯一标识 key
     * @return 从后端加载得到的结果
     */
    T load(String key);
}

