package com.ispengya.hotkey.cli.core;

/**
 * CacheResult 封装一次缓存或回源操作的结果信息，
 * 包含返回值本身、是否来自缓存以及整体耗时。
 *
 * 该结果将用于统一上报监控以及返回给业务方。
 *
 * @author ispengya
 */
public final class CacheResult<T> {

    private final T value;
    private final boolean fromCache;
    private final long costNanos;

    /**
     * 构造缓存结果。
     *
     * @param value     最终返回给业务的值
     * @param fromCache 是否来自缓存
     * @param costNanos 整体耗时，单位纳秒
     */
    public CacheResult(T value, boolean fromCache, long costNanos) {
        this.value = value;
        this.fromCache = fromCache;
        this.costNanos = costNanos;
    }

    /**
     * 获取最终结果值。
     *
     * @return 结果值对象
     */
    public T getValue() {
        return value;
    }

    /**
     * 判断结果是否来自缓存。
     *
     * @return true 表示来自缓存
     */
    public boolean isFromCache() {
        return fromCache;
    }

    /**
     * 获取整体耗时。
     *
     * @return 耗时，单位纳秒
     */
    public long getCostNanos() {
        return costNanos;
    }
}

