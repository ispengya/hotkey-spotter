package com.ispengya.hotkey.common.model;

/**
 * AccessReport 表示客户端上报的访问统计。
 *
 * <p>客户端定期（默认500ms）批量上报访问统计到 Worker，
 * Worker 根据这些统计进行热 key 判定。</p>
 *
 * @author ispengya
 */
public class AccessReport {

    /**
     * 应用名称。
     */
    private String appName;

    /**
     * 业务 key。
     */
    private String key;

    /**
     * 访问次数。
     */
    private int count;

    /**
     * 上报时间戳（毫秒）。
     */
    private long timestamp;

    /**
     * 是否成功访问（可选，用于统计成功率）。
     */
    private boolean success;

    /**
     * 响应时间（毫秒，可选）。
     */
    private long rtMillis;

    public AccessReport() {
    }

    public AccessReport(String appName, String key, int count) {
        this.appName = appName;
        this.key = key;
        this.count = count;
        this.timestamp = System.currentTimeMillis();
        this.success = true;
    }

    public AccessReport(String appName, String key, int count, long timestamp) {
        this.appName = appName;
        this.key = key;
        this.count = count;
        this.timestamp = timestamp;
        this.success = true;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public long getRtMillis() {
        return rtMillis;
    }

    public void setRtMillis(long rtMillis) {
        this.rtMillis = rtMillis;
    }

    @Override
    public String toString() {
        return "AccessReport{" +
                "appName='" + appName + '\'' +
                ", key='" + key + '\'' +
                ", count=" + count +
                ", timestamp=" + timestamp +
                ", success=" + success +
                ", rtMillis=" + rtMillis +
                '}';
    }
}
