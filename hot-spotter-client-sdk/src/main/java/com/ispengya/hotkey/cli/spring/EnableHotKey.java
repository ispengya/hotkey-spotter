package com.ispengya.hotkey.cli.spring;

import org.springframework.context.annotation.Import;
import java.lang.annotation.*;

/**
 * EnableHotKey 注解用于显式开启 hotkey 客户端自动装配，
 * 通过在应用入口类上添加该注解完成相关 Bean 的注册。
 *
 * 使用该注解后，无需再通过配置开关控制是否启用 hotkey。
 *
 * @author ispengya
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(HotKeyConfigurationSelector.class)
public @interface EnableHotKey {
}

