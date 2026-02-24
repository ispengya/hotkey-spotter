package com.ispengya.hotkey.cli.spring;

import com.ispengya.hotkey.cli.cache.DefaultLocalCache;
import com.ispengya.hotkey.cli.cache.ICache;
import com.ispengya.hotkey.cli.config.InstanceConfig;
import com.ispengya.hotkey.cli.core.CacheTemplate;
import com.ispengya.hotkey.cli.core.DefaultCacheTemplate;
import com.ispengya.hotkey.cli.origin.DefaultSafeLoadExecutor;
import com.ispengya.hotkey.cli.origin.SafeLoadExecutor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import java.util.ArrayList;

/**
 * HotKeyInstanceBeanDefinitionRegistrar 负责注册本地缓存、安全回源执行器以及缓存模板 Bean。
 *
 * @author ispengya
 */
@Deprecated
public class HotKeyInstanceBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private Environment environment;

    /**
     * 注入 Spring Environment，用于后续绑定外部化配置。
     *
     * @param environment Spring 环境对象
     */
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /**
     * 注册基于配置的默认实例 Bean。
     *
     * @param importingClassMetadata 导入配置类的元信息
     * @param registry               BeanDefinitionRegistry
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        Binder binder = Binder.get(environment);
        InstanceConfig config = binder.bind("hotkey.instance", InstanceConfig.class)
                .orElseGet(InstanceConfig::new);
        if (config.getInstanceName() == null || config.getInstanceName().isEmpty()) {
            config.setInstanceName("default");
        }
        registerInstanceBeans(config, registry);
    }

    /**
     * 为单个实例注册本地缓存、安全回源和缓存模板 Bean。
     *
     * @param config   实例配置
     * @param registry BeanDefinitionRegistry
     */
    private void registerInstanceBeans(InstanceConfig config, BeanDefinitionRegistry registry) {
        String instanceName = config.getInstanceName();
        if (StringUtils.isEmpty(instanceName)) {
            instanceName = "default";
        }
        String cacheBeanName = buildLocalCacheBeanName(instanceName);
        String safeLoadBeanName = buildSafeLoadExecutorBeanName(instanceName);
        String templateBeanName = buildCacheTemplateBeanName(instanceName);
        if (!registry.containsBeanDefinition(cacheBeanName)) {
            BeanDefinitionBuilder cacheBuilder = BeanDefinitionBuilder.genericBeanDefinition(resolveClass(config.getLocalCacheClass(), DefaultLocalCache.class, ICache.class));
            long maximumSize = defaultLong(config.getLocalCacheMaximumSize(), 10000L);
            long expireMillis = defaultLong(config.getLocalCacheExpireAfterWriteMillis(), 60000L);
            cacheBuilder.addConstructorArgValue(maximumSize);
            cacheBuilder.addConstructorArgValue(expireMillis);
            registry.registerBeanDefinition(cacheBeanName, cacheBuilder.getBeanDefinition());
        }
        if (!registry.containsBeanDefinition(safeLoadBeanName)) {
            BeanDefinitionBuilder safeLoadBuilder = BeanDefinitionBuilder.genericBeanDefinition(resolveClass(config.getSafeLoadExecutorClass(), DefaultSafeLoadExecutor.class, SafeLoadExecutor.class));
            registry.registerBeanDefinition(safeLoadBeanName, safeLoadBuilder.getBeanDefinition());
        }
        if (!registry.containsBeanDefinition(templateBeanName)) {
            BeanDefinitionBuilder templateBuilder = BeanDefinitionBuilder.genericBeanDefinition(resolveClass(config.getCacheTemplateClass(), DefaultCacheTemplate.class, CacheTemplate.class));
            templateBuilder.addConstructorArgReference(cacheBeanName);
            templateBuilder.addConstructorArgReference(safeLoadBeanName);
            registry.registerBeanDefinition(templateBeanName, templateBuilder.getBeanDefinition());
        }
    }

    private Class<?> resolveClass(String className, Class<?> defaultClass, Class<?> expectedType) {
        if (className == null || className.isEmpty()) {
            return defaultClass;
        }
        try {
            Class<?> clazz = Class.forName(className);
            if (!expectedType.isAssignableFrom(clazz)) {
                return defaultClass;
            }
            return clazz;
        } catch (ClassNotFoundException e) {
            return defaultClass;
        }
    }

    private long defaultLong(Long value, long defaultValue) {
        return value != null ? value : defaultValue;
    }

    private String buildLocalCacheBeanName(String instanceName) {
        return "hotkey." + instanceName + ".localCache";
    }

    private String buildSafeLoadExecutorBeanName(String instanceName) {
        return "hotkey." + instanceName + ".safeLoadExecutor";
    }

    private String buildCacheTemplateBeanName(String instanceName) {
        return "hotkey." + instanceName + ".cacheTemplate";
    }
}
