package com.ispengya.hotkey.server.nacos;

import com.ispengya.hotkey.common.event.EventBus;
import com.ispengya.hotkey.common.event.RuleChangeEvent;
import com.ispengya.hotkey.common.nacos.NacosClient;
import com.ispengya.hotkey.common.rule.RuleConfig;
import com.ispengya.hotkey.remoting.server.ServiceRegistration;
import com.ispengya.hotkey.server.core.RuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Nacos 集成类。
 *
 * <p>负责：
 * <ul>
 *     <li>将 Worker 服务注册到 nacos</li>
 *     <li>从 nacos 加载各应用的规则配置</li>
 *     <li>监听规则变更并实时更新 {@link RuleManager}</li>
 * </ul>
 *
 * <p>规则变更通过 {@link EventBus} + {@link RuleChangeEvent} 广播，
 * 与 Client 侧保持一致的事件模型。</p>
 *
 * @author ispengya
 */
public final class NacosIntegration {

    private static final Logger log = LoggerFactory.getLogger(NacosIntegration.class);

    private final NacosClient nacosClient;
    private final ServiceRegistration serviceRegistration;
    private final RuleManager ruleManager;
    private final String ruleDataId;
    private final List<String> appNames;

    public NacosIntegration(NacosClient nacosClient,
                            ServiceRegistration serviceRegistration,
                            RuleManager ruleManager,
                            String ruleDataId,
                            List<String> appNames) {
        this.nacosClient = nacosClient;
        this.serviceRegistration = serviceRegistration;
        this.ruleManager = ruleManager;
        this.ruleDataId = ruleDataId;
        this.appNames = appNames != null ? appNames : Collections.emptyList();
    }

    /**
     * 初始化：注册服务、加载规则并监听变更。
     */
    public void init() {
        // 1. 注册服务到 nacos
        serviceRegistration.register();

        // 2. 对每个配置的应用，加载初始规则并订阅变更
        //    NacosServerClient 在收到变更时会 post RuleChangeEvent 到 EventBus，
        //    由 RuleChangeListener 消费并更新 RuleManager
        for (String appName : appNames) {
            try {
                RuleConfig ruleConfig = nacosClient.loadRuleConfig(ruleDataId, appName);
                if (ruleConfig != null) {
                    ruleManager.updateRuleConfig(appName, ruleConfig);
                }
                nacosClient.addRuleConfigListener(ruleDataId, appName);
                log.info("Watching rule config: dataId={}, group={}", ruleDataId, appName);
            } catch (Exception e) {
                log.error("Failed to load rule config for app: {}", appName, e);
            }
        }

        log.info("NacosIntegration initialized. appNames={}", appNames);
    }

    /**
     * 获取所有已配置的应用名称。
     */
    public List<String> getAppNames() {
        return Collections.unmodifiableList(appNames);
    }

    /**
     * 关闭：注销服务。
     */
    public void shutdown() {
        serviceRegistration.deregister();
        nacosClient.shutdown();
        log.info("NacosIntegration shutdown.");
    }
}
