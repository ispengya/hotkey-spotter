package com.ispengya.hkcache.remoting.message;

import java.io.Serializable;
import java.util.Map;

/**
 * AccessReportMessage 表示一次访问统计上报的消息体。
 *
 * <p>CLI 会按一定频率聚合本地访问数据后，通过 ACCESS_REPORT 命令将该消息
 * 上报给 server 端，用于后续聚合和热 key 计算。</p>
 */
public class AccessReportMessage implements Serializable {

    private String appName;

    /**
     * 聚合窗口结束时间戳（毫秒）。
     */
    private long timestamp;

    /**
     * key 访问次数统计，key 为业务 key，value 为访问次数。
     */
    private Map<String, Integer> keyAccessCounts;

    /**
     * 无参构造函数，便于序列化框架反射创建对象。
     */
    public AccessReportMessage() {
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    /**
     * 获取聚合时间戳。
     *
     * @return 毫秒级时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 设置聚合时间戳。
     *
     * @param timestamp 毫秒级时间戳
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * 获取 key 访问次数统计。
     *
     * @return key 访问次数 map
     */
    public Map<String, Integer> getKeyAccessCounts() {
        return keyAccessCounts;
    }

    /**
     * 设置 key 访问次数统计。
     *
     * @param keyAccessCounts key 访问次数 map
     */
    public void setKeyAccessCounts(Map<String, Integer> keyAccessCounts) {
        this.keyAccessCounts = keyAccessCounts;
    }
}
