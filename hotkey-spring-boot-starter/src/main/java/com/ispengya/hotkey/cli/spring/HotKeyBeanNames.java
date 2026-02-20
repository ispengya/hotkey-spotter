package com.ispengya.hotkey.cli.spring;

/**
 * HotKeyBeanNames 统一管理 Spring 容器中 hkcache 相关 Bean 的命名，
 * 便于在多处引用时保持一致性。
 *
 * 后续如需要支持多实例，可以基于该前缀约定生成实例级 Bean 名称。
 *
 * @author ispengya
 */
public final class HotKeyBeanNames {


    /**
     * 默认 HotKeyClient Bean 名称。
     */
    public static final String DEFAULT_CLIENT = "hkcacheHotKeyClient";


    private HotKeyBeanNames() {
    }
}
