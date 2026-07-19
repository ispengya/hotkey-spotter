package com.ispengya.hotkey.common.event;

/**
 * Worker 连接断开事件。
 *
 * <p>当客户端与某台 Worker 的 Channel 断开时发布此事件，
 * 触发 NacosBridge 立即刷新 Worker 列表，避免等待 30s 定时轮询。</p>
 *
 * @author ispengya
 */
public class WorkerDisconnectEvent {
}
