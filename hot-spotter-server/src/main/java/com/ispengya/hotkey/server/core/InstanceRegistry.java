package com.ispengya.hotkey.server.core;

import java.util.Set;

/**
 * InstanceRegistry 定义应用注册表接口。
 *
 * 用于获取当前活跃或已知的 appName 列表，以便调度器遍历执行计算任务。
 */
public interface InstanceRegistry {

    /**
     * 列出所有应用名。
     *
     * @return appName 集合
     */
    Set<String> listAppNames();
}
