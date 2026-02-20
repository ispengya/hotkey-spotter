package com.ispengya.hotkey.cli.spring;

import com.ispengya.hotkey.cli.config.HotKeyProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

/**
 * HotKeyConfigurationSelector 负责根据配置选择是否导入 hkcache 相关配置，
 * 包括实例 BeanRegistrar 和自动装配配置。
 *
 * 当 hkcache 未开启时，不会向容器中注册任何相关 Bean。
 *
 * @author ispengya
 */
public class HotKeyConfigurationSelector implements DeferredImportSelector, Ordered, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        Binder binder = Binder.get(environment);
        HotKeyProperties properties = binder.bind("hkcache", Bindable.of(HotKeyProperties.class))
                .orElseGet(HotKeyProperties::new);
        if (!properties.isEnabled()) {
            return new String[] {};
        }
        return new String[] {
//                HotKeyInstanceBeanDefinitionRegistrar.class.getName(),
                HotKeyAutoConfiguration.class.getName()
        };
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }
}
