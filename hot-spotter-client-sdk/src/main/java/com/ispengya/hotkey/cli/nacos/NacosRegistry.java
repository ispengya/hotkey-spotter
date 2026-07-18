package com.ispengya.hotkey.cli.nacos;

import com.ispengya.hotkey.cli.config.HotKeyProperties;
import com.ispengya.hotkey.common.event.EventBus;
import com.ispengya.hotkey.common.event.RuleChangeEvent;
import com.ispengya.hotkey.common.event.WorkerChangeEvent;
import com.ispengya.hotkey.common.rule.RuleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Nacos 注册中心，统一管理 Nacos 相关组件。
 *
 * <p>职责：
 * <ul>
 *     <li>管理 NacosConfigClient 生命周期</li>
 *     <li>管理 WorkerDiscovery</li>
 *     <li>同步规则配置</li>
 *     <li>通过 EventBus 发布变更事件</li>
 * </ul>
 * </p>
 *
 * @author ispengya
 */
public final class NacosRegistry {

    private static final Logger log = LoggerFactory.getLogger(NacosRegistry.class);

    private final HotKeyProperties properties;
    private final NacosConfigClient nacosConfigClient;
    private final WorkerDiscovery workerDiscovery;
    private final EventBus eventBus;

    public NacosRegistry(HotKeyProperties properties, EventBus eventBus) {
        this.properties = properties;
        this.eventBus = eventBus;
        HotKeyProperties.NacosConfig nacosConfig = properties.getNacos();
        this.nacosConfigClient = new NacosConfigClient(
                nacosConfig.getServerAddr(),
                nacosConfig.getNamespace()
        );
        this.workerDiscovery = new WorkerDiscovery(
                nacosConfigClient,
                nacosConfig.getServiceName()
        );
    }

    /**
     * 初始化：加载配置、启动服务发现。
     */
    public void init() {
        try {
            nacosConfigClient.init();
            loadAndSyncRules();
            workerDiscovery.init();
            log.info("NacosRegistry initialized");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize NacosRegistry", e);
        }
    }

    /**
     * 加载规则并设置变更监听。
     */
    private void loadAndSyncRules() {
        HotKeyProperties.NacosConfig nacosConfig = properties.getNacos();

        // 加载现有规则
        RuleConfig ruleConfig = nacosConfigClient.loadRuleConfig(
                nacosConfig.getDataId(),
                nacosConfig.getGroup()
        );
        log.info("Loaded rule config: {}", ruleConfig);

        // 添加规则变更监听器（通过 EventBus 发布）
        nacosConfigClient.addRuleChangeListener((appName, rule) -> {
            log.info("Rule config changed: appName={}, rule={}", appName, rule);
            eventBus.post(new RuleChangeEvent(rule));
        });

        // 订阅规则变更
        nacosConfigClient.addRuleConfigListener(
                nacosConfig.getDataId(),
                nacosConfig.getGroup()
        );
    }

    /**
     * 初始化完成后的回调，在 HotKeyBootstrap 中调用。
     * 设置 WorkerDiscovery 的监听器，通过 EventBus 发布事件。
     */
    public void afterPropertiesSet() {
        // 监听 worker 地址变更，通过 EventBus 发布
        workerDiscovery.setAddressChangeListener(workers -> {
            log.info("Worker addresses changed: {}", workers);
            eventBus.post(new WorkerChangeEvent(workers));
        });
    }

    public NacosConfigClient getNacosConfigClient() {
        return nacosConfigClient;
    }

    public WorkerDiscovery getWorkerDiscovery() {
        return workerDiscovery;
    }

    public void shutdown() {
        workerDiscovery.shutdown();
        nacosConfigClient.shutdown();
        log.info("NacosRegistry shutdown");
    }
}
