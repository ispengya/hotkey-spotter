package com.ispengya.hotkey.cli.config;

import com.ispengya.hotkey.cli.detect.CounterMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

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

    private NacosConfig nacos = new NacosConfig();

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

    public NacosConfig getNacos() {
        return nacos;
    }

    public void setNacos(NacosConfig nacos) {
        this.nacos = nacos;
    }

    public static class DetectConfig {

        private Long reportPeriodMillis = 5000L;

        private Long queryPeriodMillis = 30000L;

        private Long queryTimeoutMillis = 3000L;

        /**
         * 访问计数器实现策略：{@code double}（双 map 无锁，默认）或 {@code lock}（单 map + 锁）。
         */
        private CounterMode counterMode = CounterMode.DOUBLE;

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

        public CounterMode getCounterMode() {
            return counterMode;
        }

        public void setCounterMode(CounterMode counterMode) {
            this.counterMode = counterMode;
        }
    }

    public static class ClientConfig {

        private Integer connectTimeoutMillis = 3000;

        private Integer workerThreads = 4;

        private Integer maxFrameBytes = 1024 * 1024;

        private Long localCacheMaximumSize = 1000L;

        private Long localCacheExpireAfterWriteMillis = 5 * 60 * 1000L;

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

    public static class NacosConfig {

        private boolean enabled = false;

        private String serverAddr = "127.0.0.1:8848";

        private String namespace = "public";

        private String serviceName = "hot-spotter-server";

        private String dataId = "hotkey-rules";

        /**
         * Nacos 配置分组。不配时自动使用 {@code hotkey.app-name}。
         * 多个服务实例共享同一套规则时，可显式指定同一个 group。
         */
        private String group;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getServerAddr() {
            return serverAddr;
        }

        public void setServerAddr(String serverAddr) {
            this.serverAddr = serverAddr;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getDataId() {
            return dataId;
        }

        public void setDataId(String dataId) {
            this.dataId = dataId;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }
    }
}
