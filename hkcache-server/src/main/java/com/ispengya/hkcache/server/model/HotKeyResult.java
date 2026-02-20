package com.ispengya.hkcache.server.model;

import java.util.Set;

/**
 * HotKeyResult 封装了 Server 端计算得出的某实例热 Key 结果。
 *
 * <p>该对象存储在 {@link com.ispengya.hkcache.server.core.HotKeyResultStore} 中，
 * 供 CLI 查询时使用。</p>
 *
 * @author ispengya
 */
public final class HotKeyResult {

    private final String appName;

    /**
     * 结果版本号。
     * 每次计算生成新结果时，版本号应递增（通常使用时间戳）。
     */
    private final long version;

    /**
     * 结果生成时间戳。
     */
    private final long lastUpdateTimeMillis;

    /**
     * 当前生效的热 Key 集合。
     */
    private final Set<String> hotKeys;

    /**
     * 构造热 Key 结果。
     *
     * @param appName              应用名
     * @param version              版本号
     * @param lastUpdateTimeMillis 更新时间
     * @param hotKeys              热 Key 集合
     */
    public HotKeyResult(String appName,
                        long version,
                        long lastUpdateTimeMillis,
                        Set<String> hotKeys) {
        this.appName = appName;
        this.version = version;
        this.lastUpdateTimeMillis = lastUpdateTimeMillis;
        this.hotKeys = hotKeys;
    }

    public String getAppName() {
        return appName;
    }

    public long getVersion() {
        return version;
    }

    public long getLastUpdateTimeMillis() {
        return lastUpdateTimeMillis;
    }

    public Set<String> getHotKeys() {
        return hotKeys;
    }

    /**
     * 基于当前时间构建一个新的 HotKeyResult。
     *
     * @param hotKeys    热 Key 集合
     * @return 新的 HotKeyResult 对象
     */
    public static HotKeyResult from(String appName, Set<String> hotKeys) {
        long now = System.currentTimeMillis();
        long version = now;
        return new HotKeyResult(appName, version, now, hotKeys);
    }
}
