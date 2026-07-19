package com.ispengya.hotkey.cli.listener;

import com.ispengya.hotkey.cli.HotKeyClient;
import com.ispengya.hotkey.cli.rule.RuleHolder;
import com.ispengya.hotkey.common.event.EventBus;
import com.ispengya.hotkey.remoting.event.HotKeyPushEvent;
import com.ispengya.hotkey.remoting.message.HotKeyViewMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 热 Key 推送监听器，从 EventBus 订阅 HotKeyPushEvent，
 * 将 Worker 推送的热 Key 变更同步到 HotKeyClient 的本地缓存。
 *
 * @author ispengya
 */
public class HotKeyPushListener {

    private static final Logger log = LoggerFactory.getLogger(HotKeyPushListener.class);

    public HotKeyPushListener(EventBus eventBus, HotKeyClient hotKeyClient) {
        eventBus.register(HotKeyPushEvent.class, event -> {
            HotKeyViewMessage message = event.getMessage();
            if (message == null) {
                return;
            }
            Map<String, HotKeyViewMessage.ViewEntry> views = message.getViews();
            if (views == null || views.isEmpty()) {
                return;
            }
            views.forEach((appName, entry) -> {
                if (entry == null) {
                    return;
                }
                if (entry.getAddedKey() != null) {
                    int duration = RuleHolder.getDuration(appName, entry.getAddedKey());
                    hotKeyClient.addHotKey(entry.getAddedKey(), duration);
                    log.debug("Hot key added: key={}, duration={}", entry.getAddedKey(), duration);
                }
                if (entry.getRemovedKey() != null) {
                    hotKeyClient.removeHotKey(entry.getRemovedKey());
                    log.debug("Hot key removed: key={}", entry.getRemovedKey());
                }
            });
        });
    }
}
