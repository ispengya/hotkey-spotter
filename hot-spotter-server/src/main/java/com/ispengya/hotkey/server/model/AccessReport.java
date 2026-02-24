package com.ispengya.hotkey.server.model;

import com.ispengya.hotkey.remoting.message.AccessReportMessage;

/**
 * AccessReport 封装了来自 CLI 的一次或一批访问统计上报。
 *
 * <p>Server 端在接收到 {@link AccessReportMessage} 后，
 * 会将其转换为 AccessReport 对象，交由聚合层进行统计。</p>
 *
 * @author ispengya
 */
public final class AccessReport {

    private final String appName;

    /**
     * 被访问的业务 key。
     */
    private final String key;

    /**
     * 上报时间戳（毫秒）。
     */
    private final long timestampMillis;

    /**
     * 本次（或本批）访问是否成功。
     * 当前 CLI 仅上报计数，默认视为成功。
     */
    private final boolean success;

    /**
     * 响应耗时（毫秒）。
     * 当前 CLI 未上报该值，默认为 0。
     */
    private final long rtMillis;

    /**
     * 访问次数。
     * 支持批量上报，单条记录可代表多次访问。
     */
    private final int count;

    /**
     * 构造访问上报对象。
     *
     * @param appName         应用名
     * @param key             业务 key
     * @param timestampMillis 时间戳
     * @param success         是否成功
     * @param rtMillis        耗时
     * @param count           访问次数
     */
    public AccessReport(String appName,
                        String key,
                        long timestampMillis,
                        boolean success,
                        long rtMillis,
                        int count) {
        this.appName = appName;
        this.key = key;
        this.timestampMillis = timestampMillis;
        this.success = success;
        this.rtMillis = rtMillis;
        this.count = count;
    }

    public String getAppName() {
        return appName;
    }

    public String getKey() {
        return key;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    public boolean isSuccess() {
        return success;
    }

    public long getRtMillis() {
        return rtMillis;
    }

    public int getCount() {
        return count;
    }
}
