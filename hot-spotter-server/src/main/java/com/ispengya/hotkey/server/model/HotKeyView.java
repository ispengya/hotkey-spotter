package com.ispengya.hotkey.server.model;

import com.ispengya.hotkey.remoting.message.HotKeyViewMessage;

/**
 * HotKeyView 用于对外展示热 Key 视图（预留）。
 *
 * <p>目前 Server 内部使用 {@link HotKeyResult} 存储，Remoting 使用
 * {@link HotKeyViewMessage} 传输，
 * 该类暂未被实际使用，可作为未来 HTTP API 的响应模型。</p>
 *
 * @author ispengya
 */
public final class HotKeyView {
    // Similar to HotKeyViewMessage but for server internal use if needed.
    // For now, let's stick to HotKeyResult for internal storage.
    // This class might be used for API responses if we had a REST API.
    // I'll skip it for now and use HotKeyResult directly or HotKeyViewMessage for remoting.
}
