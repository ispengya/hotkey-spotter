package com.ispengya.hotkey.cli.nacos;

import com.alibaba.fastjson2.JSON;
import com.ispengya.hotkey.common.nacos.NacosClient;
import com.ispengya.hotkey.common.rule.KeyRule;
import com.ispengya.hotkey.common.rule.RuleConfig;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.listener.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * NacosConfigClient 封装 nacos 配置中心和服务发现的操作。
 *
 * <p>提供以下功能：</p>
 * <ul>
 *   <li>配置读取：从 nacos 读取配置内容</li>
 *   <li>配置监听：监听配置变更并回调</li>
 *   <li>服务发现：获取服务实例列表并监听变更</li>
 *   <li>规则管理：加载和解析热 key 规则</li>
 * </ul>
 *
 * @author ispengya
 */
public class NacosConfigClient implements NacosClient {

    private static final Logger log = LoggerFactory.getLogger(NacosConfigClient.class);

    private final String serverAddr;
    private final String namespace;
    private ConfigService configService;
    private NamingService namingService;

    /**
     * 规则缓存：appName -> RuleConfig
     */
    private final Map<String, RuleConfig> ruleConfigCache = new ConcurrentHashMap<>();

    /**
     * 规则变更监听器列表。
     */
    private final List<RuleChangeListener> ruleChangeListeners = new ArrayList<>();

    public NacosConfigClient(String serverAddr, String namespace) {
        this.serverAddr = serverAddr;
        this.namespace = namespace;
    }

    /**
     * 初始化客户端，创建 ConfigService 和 NamingService。
     */
    @Override
    public void init() throws NacosException {
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);
        properties.put("namespace", namespace);

        this.configService = NacosFactory.createConfigService(properties);
        this.namingService = NacosFactory.createNamingService(properties);

        log.info("NacosConfigClient initialized. serverAddr={}, namespace={}", serverAddr, namespace);
    }

    // ==================== 配置操作 ====================

    @Override
    public String getConfig(String dataId, String group) throws NacosException {
        return configService.getConfig(dataId, group, 3000);
    }

    /**
     * 监听配置变更。
     */
    public void addConfigListener(String dataId, String group, Listener listener) throws NacosException {
        configService.addListener(dataId, group, listener);
        log.info("Added config listener. dataId={}, group={}", dataId, group);
    }

    // ==================== 服务发现 ====================

    @Override
    public List<Instance> getAllInstances(String serviceName) throws NacosException {
        return namingService.getAllInstances(serviceName);
    }

    @Override
    public void subscribe(String serviceName, EventListener listener) throws NacosException {
        namingService.subscribe(serviceName, listener);
        log.info("Subscribed to service: {}", serviceName);
    }

    @Override
    public void unsubscribe(String serviceName, EventListener listener) throws NacosException {
        namingService.unsubscribe(serviceName, listener);
    }

    @Override
    public void registerInstance(String serviceName, String ip, int port) throws NacosException {
        namingService.registerInstance(serviceName, ip, port);
    }

    @Override
    public void deregisterInstance(String serviceName, String ip, int port) throws NacosException {
        namingService.deregisterInstance(serviceName, ip, port);
    }

    // ==================== 规则管理 ====================

    @Override
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

    @Override
    public void addRuleConfigListener(String dataId, String group) {
        try {
            addConfigListener(dataId, group, new Listener() {
                @Override
                public Executor getExecutor() {
                    return null; // 使用默认线程
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("Rule config changed. group={}, config={}", group, configInfo);
                    RuleConfig ruleConfig = parseRuleConfig(group, configInfo);
                    ruleConfigCache.put(group, ruleConfig);
                    notifyRuleChange(group, ruleConfig);
                }
            });
        } catch (NacosException e) {
            log.error("Failed to add rule config listener. dataId={}, group={}", dataId, group, e);
        }
    }

    @Override
    public RuleConfig getCachedRuleConfig(String appName) {
        return ruleConfigCache.get(appName);
    }

    @Override
    public Map<String, RuleConfig> getAllCachedRuleConfigs() {
        return new ConcurrentHashMap<>(ruleConfigCache);
    }

    @Override
    public void addRuleChangeListener(RuleChangeListener listener) {
        ruleChangeListeners.add(listener);
    }

    /**
     * 解析规则配置 JSON。
     */
    private RuleConfig parseRuleConfig(String appName, String config) {
        if (config == null || config.trim().isEmpty()) {
            return new RuleConfig(appName, new ArrayList<>());
        }

        try {
            // 尝试解析为完整配置格式
            Map<String, Object> root = JSON.parseObject(config, Map.class);
            if (root.containsKey("hotkey")) {
                Map<String, Object> hotkey = (Map<String, Object>) root.get("hotkey");
                if (hotkey.containsKey("rules")) {
                    List<KeyRule> rules = JSON.parseArray(
                            JSON.toJSONString(hotkey.get("rules")),
                            KeyRule.class
                    );
                    return new RuleConfig(appName, rules);
                }
            }

            // 尝试解析为直接的规则数组
            List<KeyRule> rules = JSON.parseArray(config, KeyRule.class);
            return new RuleConfig(appName, rules);
        } catch (Exception e) {
            log.error("Failed to parse rule config. appName={}, config={}", appName, config, e);
            return new RuleConfig(appName, new ArrayList<>());
        }
    }

    /**
     * 通知规则变更。
     */
    private void notifyRuleChange(String appName, RuleConfig ruleConfig) {
        for (RuleChangeListener listener : ruleChangeListeners) {
            try {
                listener.onRuleChange(appName, ruleConfig);
            } catch (Exception e) {
                log.error("Error notifying rule change listener. appName={}", appName, e);
            }
        }
    }

    @Override
    public void shutdown() {
        try {
            if (configService != null) {
                configService.shutDown();
            }
            if (namingService != null) {
                namingService.shutDown();
            }
            log.info("NacosConfigClient shutdown.");
        } catch (NacosException e) {
            log.error("Error shutting down NacosConfigClient", e);
        }
    }
}
