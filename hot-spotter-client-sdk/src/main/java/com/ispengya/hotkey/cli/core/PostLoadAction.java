package com.ispengya.hotkey.cli.core;

/**
 * PostLoadAction 定义在一次缓存/回源流程结束后要执行的动作，
 * 例如记录监控指标、打点日志等。
 *
 * HotKeyClient 在获得 CacheResult 后会调用该回调。
 *
 * @author ispengya
 */
public interface PostLoadAction {

    /**
     * 在加载完成后被调用，用于记录监控或执行额外处理。
     *
     * @param key        本次访问的 key
     * @param value      返回给业务的结果值
     * @param fromCache  是否来自缓存
     * @param costNanos  整体耗时（纳秒）
     * @param <T>        返回值类型
     */
     <T> void onLoaded(String key, T value, boolean fromCache, long costNanos);
}

