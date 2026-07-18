package com.ispengya.hotkey.cli.nacos;

import com.alibaba.fastjson2.JSON;
import com.ispengya.hotkey.cli.rule.RuleHolder;
import com.ispengya.hotkey.common.event.EventBus;
import com.ispengya.hotkey.common.event.RuleChangeEvent;
import com.ispengya.hotkey.common.event.WorkerChangeEvent;
import com.ispengya.hotkey.common.rule.KeyRule;
import com.ispengya.hotkey.common.rule.RuleConfig;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Nacos 桥接层，统一封装应用与 Nacos 的所有交互。
 *
 * <p>职责：
 * <ul>
 *     <li>配置读写：规则配置的拉取与监听</li>
 *     <li>服务发现：Worker 节点地址的拉取、定时刷新与变更通知</li>
 *     <li>事件发布：规则变更通过 {@link RuleChangeEvent}、Worker 变更通过
 *         {@link WorkerChangeEvent}，直接走 {@link EventBus}</li>
 * </ul>
 * </p>
 *
 * @author ispengya
 */
public final class NacosBridge {

    private static final Logger log = LoggerFactory.getLogger(NacosBridge.class);

    private final String serverAddr;
    private final String namespace;
    private final EventBus eventBus;
    private ConfigService configService;
    private NamingService namingService;

    /**
     * 规则缓存：appName -> RuleConfig
     */
    private final Map<String, RuleConfig> ruleConfigCache = new ConcurrentHashMap<>();

    /**
     * Worker 地址列表（有序，用于 hash 路由）。
     */
    private final CopyOnWriteArrayList<InetSocketAddress> workerAddresses = new CopyOnWriteArrayList<>();

    /**
     * 定时刷新 Worker 地址的调度器。
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "nacos-bridge-scheduler");
        t.setDaemon(true);
        return t;
    });

    public NacosBridge(String serverAddr, String namespace, EventBus eventBus) {
        this.serverAddr = serverAddr;
        this.namespace = namespace;
        this.eventBus = eventBus;
    }

    // ==================== 初始化 ====================

    /**
     * 初始化：创建 ConfigService/NamingService，加载规则，拉取 Worker 列表并订阅变更。
     */
    public void init() {
        try {
            Properties properties = new Properties();
            properties.put("serverAddr", serverAddr);
            properties.put("namespace", namespace);

            this.configService = NacosFactory.createConfigService(properties);
            this.namingService = NacosFactory.createNamingService(properties);

            log.info("NacosBridge initialized. serverAddr={}, namespace={}", serverAddr, namespace);
        } catch (NacosException e) {
            throw new RuntimeException("Failed to init NacosBridge", e);
        }
    }

    // ==================== 配置操作 ====================

    public String getConfig(String dataId, String group) throws NacosException {
        return configService.getConfig(dataId, group, 3000);
    }

    // ==================== 规则管理 ====================

    /**
     * 加载规则配置，并写入缓存。
     */
    public RuleConfig loadRuleConfig(String dataId, String group) {
        try {
            String config = getConfig(dataId, group);
            RuleConfig ruleConfig = parseRuleConfig(group, config);
            ruleConfigCache.put(group, ruleConfig);
            return ruleConfig;
        } catch (NacosException e) {
            log.error("Failed to load rule config. dataId={}, group={}", dataId, group, e);
            return null;
        }
    }

    /**
     * 订阅规则配置变更，变更时更新缓存并发布 {@link RuleChangeEvent}。
     */
    public void addRuleConfigListener(String dataId, String group) {
        try {
            configService.addListener(dataId, group, new Listener() {
                @Override
                public java.util.concurrent.Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("Rule config changed. group={}, config={}", group, configInfo);
                    RuleConfig ruleConfig = parseRuleConfig(group, configInfo);
                    ruleConfigCache.put(group, ruleConfig);
                    eventBus.post(new RuleChangeEvent(ruleConfig));
                }
            });
        } catch (NacosException e) {
            log.error("Failed to add rule config listener. dataId={}, group={}", dataId, group, e);
        }
    }

    /**
     * 一次性完成规则同步：拉取规则 → 写入 {@link RuleHolder} → 订阅后续变更。
     */
    public void syncRules(String dataId, String group, String appName) {
        RuleConfig ruleConfig = loadRuleConfig(dataId, group);
        log.info("Loaded rule config: {}", ruleConfig);

        if (ruleConfig != null) {
            RuleHolder.updateRuleConfig(appName, ruleConfig);
        }

        addRuleConfigListener(dataId, group);
    }

    public RuleConfig getCachedRuleConfig(String appName) {
        return ruleConfigCache.get(appName);
    }

    public Map<String, RuleConfig> getAllCachedRuleConfigs() {
        return new ConcurrentHashMap<>(ruleConfigCache);
    }

    // ==================== 服务发现 ====================

    /**
     * 启动 Worker 发现：首次拉取 + 订阅变更 + 定时兜底刷新。
     */
    public void startWorkerDiscovery(String serviceName) {
        // 首次拉取并发布
        refreshWorkerAddresses(serviceName);

        // 订阅变更
        try {
            namingService.subscribe(serviceName, event -> {
                log.info("Worker list changed: {}", event);
                refreshWorkerAddresses(serviceName);
            });
        } catch (NacosException e) {
            log.error("Failed to subscribe to service: {}", serviceName, e);
        }

        // 定时兜底
        scheduler.scheduleAtFixedRate(
                () -> refreshWorkerAddresses(serviceName),
                30, 30, TimeUnit.SECONDS);

        log.info("Worker discovery started. serviceName={}, workers={}", serviceName, workerAddresses);
    }

    private void refreshWorkerAddresses(String serviceName) {
        try {
            List<Instance> instances = namingService.getAllInstances(serviceName);
            List<InetSocketAddress> newAddresses = new ArrayList<>();
            for (Instance instance : instances) {
                if (instance.isEnabled() && instance.isHealthy()) {
                    newAddresses.add(new InetSocketAddress(instance.getIp(), instance.getPort()));
                }
            }

            workerAddresses.clear();
            workerAddresses.addAll(newAddresses);

            // 直接通过 EventBus 发布，无需中间 Consumer
            eventBus.post(new WorkerChangeEvent(new ArrayList<>(workerAddresses)));

            log.debug("Refreshed worker addresses: {}", workerAddresses);
        } catch (Exception e) {
            log.error("Failed to refresh worker addresses", e);
        }
    }

    /**
     * 获取当前 Worker 地址列表。
     */
    public List<InetSocketAddress> getWorkerAddresses() {
        return new ArrayList<>(workerAddresses);
    }

    // ==================== 内部工具 ====================

    private RuleConfig parseRuleConfig(String appName, String config) {
        if (config == null || config.trim().isEmpty()) {
            return new RuleConfig(appName, new ArrayList<>());
        }

        try {
            Map<String, Object> root = JSON.parseObject(config, Map.class);
            if (root.containsKey("hotkey")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> hotkey = (Map<String, Object>) root.get("hotkey");
                if (hotkey.containsKey("rules")) {
                    List<KeyRule> rules = JSON.parseArray(
                            JSON.toJSONString(hotkey.get("rules")),
                            KeyRule.class
                    );
                    return new RuleConfig(appName, rules);
                }
            }

            List<KeyRule> rules = JSON.parseArray(config, KeyRule.class);
            return new RuleConfig(appName, rules);
        } catch (Exception e) {
            log.error("Failed to parse rule config. appName={}, config={}", appName, config, e);
            return new RuleConfig(appName, new ArrayList<>());
        }
    }

    // ==================== 关闭 ====================

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (configService != null) {
                configService.shutDown();
            }
            if (namingService != null) {
                namingService.shutDown();
            }
            log.info("NacosBridge shutdown.");
        } catch (NacosException e) {
            log.error("Error shutting down NacosBridge", e);
        }
    }
}
