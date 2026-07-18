package com.ispengya.hotkey.remoting.event;

import com.ispengya.hotkey.remoting.message.HotKeyViewMessage;

/**
 * 热 Key 推送事件。
 *
 * @author ispengya
 */
public class HotKeyPushEvent {

    private final HotKeyViewMessage message;

    public HotKeyPushEvent(HotKeyViewMessage message) {
        this.message = message;
    }

    public HotKeyViewMessage getMessage() {
        return message;
    }
}
