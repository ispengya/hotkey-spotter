package com.ispengya.hotkey.cli.config;

/**
 * InstanceConfig 描述单个 HotKeyClient 实例的配置，
 * 当前仅包含实例名称等基础信息。
 *
 * 后续可以扩展服务端地址、策略等更多参数。
 *
 * @author ispengya
 */
public class InstanceConfig {

    private String instanceName;

    private String cacheTemplateClass;

    private String localCacheClass;

    private String safeLoadExecutorClass;

    private Long localCacheMaximumSize;

    private Long localCacheExpireAfterWriteMillis;

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public String getCacheTemplateClass() {
        return cacheTemplateClass;
    }

    public void setCacheTemplateClass(String cacheTemplateClass) {
        this.cacheTemplateClass = cacheTemplateClass;
    }

    public String getLocalCacheClass() {
        return localCacheClass;
    }

    public void setLocalCacheClass(String localCacheClass) {
        this.localCacheClass = localCacheClass;
    }

    public String getSafeLoadExecutorClass() {
        return safeLoadExecutorClass;
    }

    public void setSafeLoadExecutorClass(String safeLoadExecutorClass) {
        this.safeLoadExecutorClass = safeLoadExecutorClass;
    }

    public Long getLocalCacheMaximumSize() {
        return localCacheMaximumSize;
    }

    public void setLocalCacheMaximumSize(Long localCacheMaximumSize) {
        this.localCacheMaximumSize = localCacheMaximumSize;
    }

    public Long getLocalCacheExpireAfterWriteMillis() {
        return localCacheExpireAfterWriteMillis;
    }

    public void setLocalCacheExpireAfterWriteMillis(Long localCacheExpireAfterWriteMillis) {
        this.localCacheExpireAfterWriteMillis = localCacheExpireAfterWriteMillis;
    }
}
