package com.ispengya.hotkey.cli.listener;

import com.ispengya.hotkey.common.event.EventBus;
import com.ispengya.hotkey.common.event.RuleChangeEvent;
import com.ispengya.hotkey.common.rule.RuleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 规则变更监听器，从 EventBus 订阅 RuleChangeEvent。
 *
 * @author ispengya
 */
public class RuleChangeListener {

    private static final Logger log = LoggerFactory.getLogger(RuleChangeListener.class);

    public RuleChangeListener(EventBus eventBus) {
        eventBus.register(RuleChangeEvent.class, this::onRuleChange);
    }

    private void onRuleChange(RuleChangeEvent event) {
        RuleConfig rule = event.getRuleConfig();
        log.info("Rule config changed: rule={}", rule);
        // TODO: 业务逻辑
    }
}
