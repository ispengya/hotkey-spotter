package com.ispengya.hotkey.remoting.message;

import java.io.Serializable;
import java.util.Map;

/**
 * HotKeyQueryRequest 表示 CLI 向 server 端发起的热 key 查询请求。
 *
 * <p>请求中包含各实例上一次收到的版本号，server 可以根据版本号决定
 * 是否返回最新热 key 视图。</p>
 */
public class HotKeyQueryRequest implements Serializable {

    private Map<String, Long> lastVersions;

    /**
     * 无参构造函数，便于序列化框架使用。
     */
    public HotKeyQueryRequest() {
    }

    public HotKeyQueryRequest(Map<String, Long> lastVersions) {
        this.lastVersions = lastVersions;
    }

    public Map<String, Long> getLastVersions() {
        return lastVersions;
    }

    public void setLastVersions(Map<String, Long> lastVersions) {
        this.lastVersions = lastVersions;
    }
}
