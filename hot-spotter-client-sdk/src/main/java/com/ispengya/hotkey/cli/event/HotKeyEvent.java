package com.ispengya.hotkey.cli.event;

import com.ispengya.hotkey.remoting.message.HotKeyViewMessage;

/**
 * 热 Key 推送事件。
 *
 * @author ispengya
 */
public final class HotKeyEvent {

    private final HotKeyViewMessage message;

    public HotKeyEvent(HotKeyViewMessage message) {
        this.message = message;
    }

    public HotKeyViewMessage getMessage() {
        return message;
    }
}
