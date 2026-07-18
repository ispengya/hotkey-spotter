package com.ispengya.hotkey.server.nacos;

import com.ispengya.hotkey.common.nacos.NacosConfigClient;
import com.ispengya.hotkey.common.rule.RuleConfig;
import com.ispengya.hotkey.remoting.server.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos 集成类。
 *
 * <p>负责：
 * <ul>
 *     <li>从 nacos 监听规则变更</li>
 *     <li>将 Worker 服务注册到 nacos</li>
 *     <li>管理规则配置</li>
 * </ul>
 * </p>
 *
 * @author ispengya
 */
public final class NacosIntegration {

    private static final Logger log = LoggerFactory.getLogger(NacosIntegration.class);

    private final NacosConfigClient nacosClient;
    private final ServiceRegistration serviceRegistration;
    private final ConcurrentHashMap<String, RuleConfig> ruleConfigs = new ConcurrentHashMap<>();
    private volatile RuleChangeListener ruleChangeListener;

    public NacosIntegration(NacosConfigClient nacosClient,
                            ServiceRegistration serviceRegistration) {
        this.nacosClient = nacosClient;
        this.serviceRegistration = serviceRegistration;
    }

    /**
     * 设置规则变更监听器。
     */
    public void setRuleChangeListener(RuleChangeListener listener) {
        this.ruleChangeListener = listener;
    }

    /**
     * 初始化：注册服务并监听规则变更。
     */
    public void init() {
        // 注册服务到 nacos
        serviceRegistration.register();

        // 监听规则变更
        nacosClient.addRuleChangeListener((appName, ruleConfig) -> {
            log.info("Rule changed: appName={}", appName);
            ruleConfigs.put(appName, ruleConfig);
            RuleChangeListener listener = ruleChangeListener;
            if (listener != null) {
                listener.onRuleChange(appName, ruleConfig);
            }
        });

        log.info("NacosIntegration initialized.");
    }

    /**
     * 加载指定应用的规则配置。
     */
    public RuleConfig loadRuleConfig(String dataId, String group) {
        return nacosClient.loadRuleConfig(dataId, group);
    }

    /**
     * 获取指定应用的规则配置。
     */
    public RuleConfig getRuleConfig(String appName) {
        return ruleConfigs.get(appName);
    }

    /**
     * 获取所有规则配置。
     */
    public Map<String, RuleConfig> getAllRuleConfigs() {
        return new ConcurrentHashMap<>(ruleConfigs);
    }

    /**
     * 关闭：注销服务。
     */
    public void shutdown() {
        serviceRegistration.deregister();
        nacosClient.shutdown();
        log.info("NacosIntegration shutdown.");
    }

    /**
     * 规则变更监听器接口。
     */
    public interface RuleChangeListener {
        void onRuleChange(String appName, RuleConfig ruleConfig);
    }
}
