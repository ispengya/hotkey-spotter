package com.ispengya.hotkey.remoting.message;

import com.ispengya.hotkey.common.rule.RuleConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * 规则变更消息。
 *
 * <p>Worker 检测到规则变更时，通过此消息通知所有连接的 Client。</p>
 */
public class RuleChangeMessage {

    private String appName;
    private RuleConfig ruleConfig;

    public RuleChangeMessage() {
    }

    public RuleChangeMessage(String appName, RuleConfig ruleConfig) {
        this.appName = appName;
        this.ruleConfig = ruleConfig;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public RuleConfig getRuleConfig() {
        return ruleConfig;
    }

    public void setRuleConfig(RuleConfig ruleConfig) {
        this.ruleConfig = ruleConfig;
    }

    @Override
    public String toString() {
        return "RuleChangeMessage{" +
                "appName='" + appName + '\'' +
                '}';
    }
}
