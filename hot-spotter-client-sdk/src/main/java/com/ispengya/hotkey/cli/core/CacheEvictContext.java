package com.ispengya.hotkey.cli.core;

/**
 * CacheEvictContext 描述一次缓存失效操作的上下文，
 * 主要包含 key 以及触发失效时的时间戳。
 *
 * 该上下文将被 CacheTemplate 用于执行精确失效操作。
 *
 * @author ispengya
 */
public final class CacheEvictContext {

    private final String key;
    private final long nowMillis;

    /**
     * 构造缓存失效上下文。
     *
     * @param key       需要失效的缓存 key
     * @param nowMillis 触发失效的时间戳
     */
    public CacheEvictContext(String key, long nowMillis) {
        this.key = key;
        this.nowMillis = nowMillis;
    }

    /**
     * 获取需要失效的 key。
     *
     * @return 缓存 key
     */
    public String getKey() {
        return key;
    }

    /**
     * 获取触发失效的时间戳。
     *
     * @return 毫秒级时间戳
     */
    public long getNowMillis() {
        return nowMillis;
    }
}
