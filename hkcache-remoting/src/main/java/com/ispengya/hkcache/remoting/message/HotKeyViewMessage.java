package com.ispengya.hkcache.remoting.message;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/**
 * HotKeyViewMessage 表示 server 端计算出的热 key 视图。
 *
 * <p>CLI 周期性向 server 端发起 HOT_KEY_QUERY 请求后，server 将当前版本的
 * 热 key 集合封装在该消息中返回。</p>
 */
public class HotKeyViewMessage implements Serializable {

    public static final class ViewEntry implements Serializable {
        private long version;
        private Set<String> hotKeys;
        private String addedKey;
        private String removedKey;

        public ViewEntry() {
        }

        public ViewEntry(long version, Set<String> hotKeys) {
            this.version = version;
            this.hotKeys = hotKeys;
        }

        public long getVersion() {
            return version;
        }

        public void setVersion(long version) {
            this.version = version;
        }

        public Set<String> getHotKeys() {
            return hotKeys;
        }

        public void setHotKeys(Set<String> hotKeys) {
            this.hotKeys = hotKeys;
        }

        public String getAddedKey() {
            return addedKey;
        }

        public void setAddedKey(String addedKey) {
            this.addedKey = addedKey;
        }

        public String getRemovedKey() {
            return removedKey;
        }

        public void setRemovedKey(String removedKey) {
            this.removedKey = removedKey;
        }
    }

    /**
     * 热 key 结果的版本号，用于增量拉取或幂等判断。
     */
    private long version;

    private Map<String, ViewEntry> views;

    /**
     * 无参构造函数，便于序列化框架使用。
     */
    public HotKeyViewMessage() {
    }

    /**
     * 获取结果版本号。
     */
    public long getVersion() {
        return version;
    }

    /**
     * 设置结果版本号。
     */
    public void setVersion(long version) {
        this.version = version;
    }


    public Map<String, ViewEntry> getViews() {
        return views;
    }

    public void setViews(Map<String, ViewEntry> views) {
        this.views = views;
    }
}
