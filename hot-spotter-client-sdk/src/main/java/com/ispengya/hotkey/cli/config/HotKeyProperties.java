package com.ispengya.hotkey.cli.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * HotKeyProperties 定义 hotkey 客户端在 Spring Boot 中的外部化配置，
 * 通过配置文件中以 "hotkey" 为前缀的属性进行绑定。
 *
 * @author ispengya
 */
@ConfigurationProperties(prefix = "hotkey")
public class HotKeyProperties {

    private boolean enabled = true;

    private String appName;

    private DetectConfig detect = new DetectConfig();

    private ClientConfig client = new ClientConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public DetectConfig getDetect() {
        return detect;
    }

    public void setDetect(DetectConfig detect) {
        this.detect = detect;
    }

    public ClientConfig getClient() {
        return client;
    }

    public void setClient(ClientConfig client) {
        this.client = client;
    }

    public static class DetectConfig {

        private Long reportPeriodMillis = 5000L;

        private Long queryPeriodMillis = 30000L;

        private Long queryTimeoutMillis = 3000L;

        public Long getReportPeriodMillis() {
            return reportPeriodMillis;
        }

        public void setReportPeriodMillis(Long reportPeriodMillis) {
            this.reportPeriodMillis = reportPeriodMillis;
        }

        public Long getQueryPeriodMillis() {
            return queryPeriodMillis;
        }

        public void setQueryPeriodMillis(Long queryPeriodMillis) {
            this.queryPeriodMillis = queryPeriodMillis;
        }

        public Long getQueryTimeoutMillis() {
            return queryTimeoutMillis;
        }

        public void setQueryTimeoutMillis(Long queryTimeoutMillis) {
            this.queryTimeoutMillis = queryTimeoutMillis;
        }
    }

    public static class ClientConfig {

        private List<String> serverAddresses;

        private Integer connectTimeoutMillis = 3000;

        private Integer workerThreads = 4;

        private Integer maxFrameBytes = 1024 * 1024;

        private Integer pushPoolSize = 1;

        private Integer reportPoolSize = 2;

        private String cacheTemplateClass;

        private String localCacheClass;

        private String safeLoadExecutorClass;

        private Long localCacheMaximumSize = 1000L;

        private Long localCacheExpireAfterWriteMillis = 5 * 60 * 1000L;

        public List<String> getServerAddresses() {
            return serverAddresses;
        }

        public void setServerAddresses(List<String> serverAddresses) {
            this.serverAddresses = serverAddresses;
        }

        public Integer getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public void setConnectTimeoutMillis(Integer connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }

        public Integer getWorkerThreads() {
            return workerThreads;
        }

        public void setWorkerThreads(Integer workerThreads) {
            this.workerThreads = workerThreads;
        }

        public Integer getMaxFrameBytes() {
            return maxFrameBytes;
        }

        public void setMaxFrameBytes(Integer maxFrameBytes) {
            this.maxFrameBytes = maxFrameBytes;
        }

        public Integer getPushPoolSize() {
            return pushPoolSize;
        }

        public void setPushPoolSize(Integer pushPoolSize) {
            this.pushPoolSize = pushPoolSize;
        }

        public Integer getReportPoolSize() {
            return reportPoolSize;
        }

        public void setReportPoolSize(Integer reportPoolSize) {
            this.reportPoolSize = reportPoolSize;
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
}
