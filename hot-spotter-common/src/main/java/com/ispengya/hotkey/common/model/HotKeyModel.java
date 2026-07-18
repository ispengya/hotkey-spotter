package com.ispengya.hotkey.common.model;

/**
 * HotKeyModel 表示一个热 key 的信息。
 *
 * <p>用于 Worker 推送热 key 到 Client，以及 Client 端管理本地热 key 集合。</p>
 *
 * @author ispengya
 */
public class HotKeyModel {

    /**
     * 应用名称。
     */
    private String appName;

    /**
     * 业务 key。
     */
    private String key;

    /**
     * 热 key 创建时间戳（毫秒）。
     */
    private long createTime;

    /**
     * 缓存时长（秒）。
     * 来自规则配置的 duration 字段。
     */
    private int duration;

    /**
     * 是否是删除事件。
     */
    private boolean remove;

    public HotKeyModel() {
    }

    public HotKeyModel(String appName, String key) {
        this.appName = appName;
        this.key = key;
        this.createTime = System.currentTimeMillis();
    }

    public HotKeyModel(String appName, String key, long createTime) {
        this.appName = appName;
        this.key = key;
        this.createTime = createTime;
    }

    /**
     * 生成缓存 key。
     * 格式: appName-keyType-key
     */
    public String cacheKey() {
        return appName + "-" + key;
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

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public boolean isRemove() {
        return remove;
    }

    public void setRemove(boolean remove) {
        this.remove = remove;
    }

    @Override
    public String toString() {
        return "HotKeyModel{" +
                "appName='" + appName + '\'' +
                ", key='" + key + '\'' +
                ", createTime=" + createTime +
                ", duration=" + duration +
                ", remove=" + remove +
                '}';
    }
}
