package com.ispengya.hotkey.cli.nacos;

import com.ispengya.hotkey.common.rule.RuleConfig;
import com.ispengya.hotkey.cli.config.HotKeyProperties;
import com.ispengya.hotkey.cli.rule.RuleHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nacos 注册中心统一管理。
 *
 * <p>负责：
 * <ul>
 *     <li>初始化 NacosConfigClient</li>
 *     <li>初始化 WorkerDiscovery</li>
 *     <li>同步规则配置到 RuleHolder</li>
 *     <li>统一生命周期管理</li>
 * </ul>
 * </p>
 *
 * @author ispengya
 */
public class NacosRegistry {

    private static final Logger log = LoggerFactory.getLogger(NacosRegistry.class);

    private final NacosConfigClient nacosConfigClient;
    private final WorkerDiscovery workerDiscovery;
    private final String appName;

    public NacosRegistry(HotKeyProperties properties) {
        this.appName = properties.getAppName();
        HotKeyProperties.NacosConfig nacosConfig = properties.getNacos();

        // 初始化 NacosConfigClient
        this.nacosConfigClient = new NacosConfigClient(
                nacosConfig.getServerAddr(),
                nacosConfig.getNamespace()
        );

        // 初始化 WorkerDiscovery
        this.workerDiscovery = new WorkerDiscovery(
                nacosConfigClient,
                nacosConfig.getServiceName()
        );
    }

    /**
     * 初始化：连接 nacos、订阅规则变更、加载已有规则、启动服务发现。
     */
    public void init() {
        // 初始化 nacos 客户端
        try {
            nacosConfigClient.init();
            log.info("NacosConfigClient initialized: appName={}", appName);
        } catch (Exception e) {
            log.error("Failed to initialize NacosConfigClient", e);
            throw new RuntimeException("Failed to initialize NacosConfigClient", e);
        }

        // 添加规则变更监听器，同步更新 RuleHolder
        nacosConfigClient.addRuleChangeListener(new NacosConfigClient.RuleChangeListener() {
            @Override
            public void onRuleChange(String appName, RuleConfig ruleConfig) {
                RuleHolder.updateRuleConfig(appName, ruleConfig);
                log.info("RuleHolder updated for app: {}", appName);
            }
        });

        // 加载已有的规则配置
        if (appName != null && !appName.isEmpty()) {
            RuleConfig existingConfig = nacosConfigClient.getCachedRuleConfig(appName);
            if (existingConfig != null) {
                RuleHolder.updateRuleConfig(appName, existingConfig);
                log.info("Loaded existing rule config for app: {}", appName);
            }
        }

        // 初始化服务发现
        workerDiscovery.init();
        log.info("WorkerDiscovery initialized: appName={}", appName);
    }

    /**
     * 获取 Worker 发现客户端。
     */
    public WorkerDiscovery getWorkerDiscovery() {
        return workerDiscovery;
    }

    /**
     * 获取 Nacos 客户端。
     */
    public NacosConfigClient getNacosConfigClient() {
        return nacosConfigClient;
    }

    /**
     * 关闭。
     */
    public void shutdown() {
        workerDiscovery.shutdown();
        nacosConfigClient.shutdown();
        log.info("NacosRegistry shutdown: appName={}", appName);
    }
}
