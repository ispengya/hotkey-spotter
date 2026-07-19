package com.ispengya.hotkey.cli.listener;

import com.ispengya.hotkey.cli.rule.RuleHolder;
import com.ispengya.hotkey.common.event.EventBus;
import com.ispengya.hotkey.common.event.RuleChangeEvent;
import com.ispengya.hotkey.common.rule.RuleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 规则变更监听器，从 EventBus 订阅 RuleChangeEvent。
 *
 * <p>当 Nacos 上的规则配置发生变更时，NacosBridge 会发布 RuleChangeEvent，
 * 本监听器收到事件后将最新的 RuleConfig 更新到 RuleHolder，确保客户端
 * 上报过滤和热 key 判定使用最新的规则。</p>
 *
 * @author ispengya
 */
public class RuleChangeListener {

    private static final Logger log = LoggerFactory.getLogger(RuleChangeListener.class);

    public RuleChangeListener(EventBus eventBus) {
        eventBus.register(RuleChangeEvent.class, this::onRuleChange);
    }

    private void onRuleChange(RuleChangeEvent event) {
        RuleConfig ruleConfig = event.getRuleConfig();
        if (ruleConfig == null) {
            return;
        }
        String appName = ruleConfig.getAppName();
        RuleHolder.updateRuleConfig(appName, ruleConfig);
        log.info("Rule config changed and updated to RuleHolder: appName={}, ruleCount={}",
                appName, ruleConfig.getRules().size());
    }
}
