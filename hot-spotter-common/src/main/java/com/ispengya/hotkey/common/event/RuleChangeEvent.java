package com.ispengya.hotkey.common.event;

import com.ispengya.hotkey.common.rule.RuleConfig;

/**
 * 规则配置变更事件。
 *
 * @author ispengya
 */
public final class RuleChangeEvent {

    private final RuleConfig ruleConfig;

    public RuleChangeEvent(RuleConfig ruleConfig) {
        this.ruleConfig = ruleConfig;
    }

    public RuleConfig getRuleConfig() {
        return ruleConfig;
    }
}
