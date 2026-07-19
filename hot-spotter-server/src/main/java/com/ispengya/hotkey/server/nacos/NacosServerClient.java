package com.ispengya.hotkey.server.nacos;

import com.ispengya.hotkey.common.event.EventBus;
import com.ispengya.hotkey.common.event.RuleChangeEvent;
import com.ispengya.hotkey.common.nacos.NacosClient;
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
 * Nacos 服务端客户端实现。
 *
 * <p>服务端使用的 Nacos 客户端，实现 NacosClient 接口。
 * 规则变更通过 {@link EventBus} 发布 {@link RuleChangeEvent}，
 * 与 Client 侧 {@code NacosBridge} 保持一致的事件模型。</p>
 *
 * @author ispengya
 */
public class NacosServerClient implements NacosClient {

    private static final Logger log = LoggerFactory.getLogger(NacosServerClient.class);

    private final String serverAddr;
    private final String namespace;
    private final EventBus eventBus;
    private ConfigService configService;
    private NamingService namingService;

    private final Map<String, RuleConfig> ruleConfigCache = new ConcurrentHashMap<>();

    public NacosServerClient(String serverAddr, String namespace, EventBus eventBus) {
        this.serverAddr = serverAddr;
        this.namespace = namespace;
        this.eventBus = eventBus;
    }

    @Override
    public void init() throws NacosException {
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);
        // 仅当 namespace 非空时设置，空字符串会导致 Nacos 连接失败
        if (namespace != null && !namespace.isEmpty()) {
            properties.put("namespace", namespace);
        }

        this.configService = NacosFactory.createConfigService(properties);
        this.namingService = NacosFactory.createNamingService(properties);

        log.info("NacosServerClient initialized. serverAddr={}, namespace={}", serverAddr, namespace);
    }

    @Override
    public String getConfig(String dataId, String group) throws NacosException {
        return configService.getConfig(dataId, group, 3000);
    }

    @Override
    public List<Instance> getAllInstances(String serviceName) throws NacosException {
        return namingService.getAllInstances(serviceName);
    }

    @Override
    public void subscribe(String serviceName, EventListener listener) throws NacosException {
        namingService.subscribe(serviceName, listener);
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
            configService.addListener(dataId, group, new Listener() {
                @Override
                public Executor getExecutor() {
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

    @Override
    public RuleConfig getCachedRuleConfig(String appName) {
        return ruleConfigCache.get(appName);
    }

    @Override
    public Map<String, RuleConfig> getAllCachedRuleConfigs() {
        return new ConcurrentHashMap<>(ruleConfigCache);
    }

    /**
     * 解析规则配置。委托给 {@link RuleConfig#parse(String, String)}，
     * 支持 YAML 和 JSON 两种格式。
     */
    private RuleConfig parseRuleConfig(String appName, String config) {
        return RuleConfig.parse(appName, config);
    }

    @Override
    public void shutdown() {
        try {
            if (configService != null) configService.shutDown();
            if (namingService != null) namingService.shutDown();
            log.info("NacosServerClient shutdown.");
        } catch (NacosException e) {
            log.error("Error shutting down NacosServerClient", e);
        }
    }
}
