package com.ispengya.hotkey.remoting.protocol;

/**
 * CommandType 定义 remoting 层支持的命令类型。
 *
 * <p>目前包含三类基础指令：</p>
 * <ul>
 *     <li>ACCESS_REPORT：CLI 上报访问统计数据</li>
 *     <li>HOT_KEY_QUERY：CLI 拉取当前热 key 视图</li>
 *     <li>ADMIN_PING：管理类心跳或探活请求</li>
 * </ul>
 */
public enum CommandType {

    ACCESS_REPORT,
    HOT_KEY_QUERY,
    ADMIN_PING,
    HOT_KEY_PUSH,
    PUSH_CHANNEL_REGISTER
}
