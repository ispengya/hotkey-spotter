package com.ispengya.hotkey.cli.spring;

import cn.hutool.core.collection.CollUtil;
import com.ispengya.hkcache.remoting.protocol.JdkSerializer;
import com.ispengya.hkcache.remoting.client.ClientRequestSender;
import com.ispengya.hkcache.remoting.client.HotKeyRemotingClient;
import com.ispengya.hkcache.remoting.client.NettyClient;
import com.ispengya.hkcache.remoting.client.NettyClientConfig;
import com.ispengya.hkcache.remoting.protocol.Serializer;
import com.ispengya.hotkey.cli.config.HotKeyProperties;
import com.ispengya.hotkey.cli.cache.DefaultLocalCache;
import com.ispengya.hotkey.cli.cache.ICache;
import com.ispengya.hotkey.cli.core.CacheTemplate;
import com.ispengya.hotkey.cli.core.DefaultCacheTemplate;
import com.ispengya.hotkey.cli.core.HotKeyClient;
import com.ispengya.hotkey.cli.core.PostLoadAction;
import com.ispengya.hotkey.cli.detect.HotKeyDetector;
import com.ispengya.hotkey.cli.detect.HotKeySet;
import com.ispengya.hotkey.cli.origin.DefaultSafeLoadExecutor;
import com.ispengya.hotkey.cli.origin.SafeLoadExecutor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HotKeyAutoConfiguration 提供 hkcache 在 Spring Boot 环境下的自动装配，
 * 在检测到 HotKeyClient 存在且配置开启时，自动注册相关 Bean。
 *
 * 默认实现基于本地缓存和空实现的探测与监控，后续可按需替换。
 *
 * @author ispengya
 */
@Configuration
@ConditionalOnClass(HotKeyClient.class)
@EnableConfigurationProperties(HotKeyProperties.class)
public class HotKeyAutoConfiguration {


    /**
     * 注册 HotKeyClientManager，用于集中管理多实例客户端。
     *
     * @return HotKeyClientManager 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @Deprecated
    public HotKeyClientManager hkcacheHotKeyClientManager() {
        return new HotKeyClientManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public PostLoadAction hkcachePostLoadAction() {
        return new PostLoadAction() {
            @Override
            public <T> void onLoaded(String key, T value, boolean fromCache, long costNanos) {
                // 空实现
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public Serializer hkcacheSerializer() {
        return new JdkSerializer();
    }

    @Bean
    @ConditionalOnMissingBean
    public NettyClient hkcacheNettyClient(HotKeyProperties properties) {
        HotKeyProperties.ClientConfig clientConfig = properties.getClient();
        List<InetSocketAddress> serverAddresses = buildServerAddresses(clientConfig);
        int connectTimeoutMillis = clientConfig.getConnectTimeoutMillis();
        int workerThreads = clientConfig.getWorkerThreads();
        int maxFrameBytes = clientConfig.getMaxFrameBytes();
        int pushPoolSize = clientConfig.getPushPoolSize();
        int reportPoolSize = clientConfig.getReportPoolSize();
        String appName = properties.getAppName();
        NettyClientConfig config = new NettyClientConfig(
                serverAddresses,
                connectTimeoutMillis,
                workerThreads,
                maxFrameBytes,
                pushPoolSize,
                reportPoolSize,
                appName
        );
        NettyClient client = new NettyClient(config);
        client.start();
        return client;
    }

    @Bean
    @ConditionalOnMissingBean
    public HotKeyRemotingClient hkcacheRemotingClient(HotKeyProperties properties,
                                                      Serializer serializer,
                                                      NettyClient nettyClient) {
        ClientRequestSender sender = new ClientRequestSender(nettyClient);
        String appName = properties.getAppName();
        HotKeyRemotingClient client = new HotKeyRemotingClient(serializer, sender, appName);
        client.registerPushChannel();
        return client;
    }

    @Bean
    @ConditionalOnMissingBean
    public HotKeySet hkcacheHotKeySet() {
        return new HotKeySet();
    }

    @Bean
    @ConditionalOnMissingBean
    public ICache<String, Object> hkcacheLocalCache(HotKeyProperties properties) {
        HotKeyProperties.ClientConfig client = properties.getClient();
        long maximumSize = client.getLocalCacheMaximumSize();
        long expireMillis = client.getLocalCacheExpireAfterWriteMillis();
        String className = client.getLocalCacheClass();
        Class<?> clazz = resolveClass(className, DefaultLocalCache.class, ICache.class);
        if (clazz == DefaultLocalCache.class) {
            return new DefaultLocalCache<>(maximumSize, expireMillis);
        }
        try {
            Class<? extends ICache> cacheClass = (Class<? extends ICache>) clazz;
            return (ICache<String, Object>) cacheClass
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (Exception e) {
            return new DefaultLocalCache<>(maximumSize, expireMillis);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public SafeLoadExecutor hkcacheSafeLoadExecutor(HotKeyProperties properties) {
        HotKeyProperties.ClientConfig client = properties.getClient();
        String className = client.getSafeLoadExecutorClass();
        Class<?> clazz = resolveClass(className, DefaultSafeLoadExecutor.class, SafeLoadExecutor.class);
        if (clazz == DefaultSafeLoadExecutor.class) {
            return new DefaultSafeLoadExecutor();
        }
        try {
            return (SafeLoadExecutor) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return new DefaultSafeLoadExecutor();
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheTemplate hkcacheCacheTemplate(HotKeyProperties properties,
                                              ICache<String, Object> hkcacheLocalCache,
                                              SafeLoadExecutor hkcacheSafeLoadExecutor) {
        HotKeyProperties.ClientConfig client = properties.getClient();
        String className = client.getCacheTemplateClass();
        Class<?> clazz = resolveClass(className, DefaultCacheTemplate.class, CacheTemplate.class);
        if (clazz == DefaultCacheTemplate.class) {
            return new DefaultCacheTemplate(hkcacheLocalCache, hkcacheSafeLoadExecutor);
        }
        try {
            return (CacheTemplate) clazz
                    .getConstructor(ICache.class, SafeLoadExecutor.class)
                    .newInstance(hkcacheLocalCache, hkcacheSafeLoadExecutor);
        } catch (Exception e) {
            return new DefaultCacheTemplate(hkcacheLocalCache, hkcacheSafeLoadExecutor);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public HotKeyDetector hkcacheHotKeyDetector(HotKeyProperties properties,
                                                HotKeyRemotingClient remotingClient,
                                                HotKeySet hotKeySet) {
        HotKeyProperties.DetectConfig detectConfig = properties.getDetect();
        long reportPeriodMillis = detectConfig.getReportPeriodMillis();
        long queryPeriodMillis = detectConfig.getQueryPeriodMillis();
        long queryTimeoutMillis = detectConfig.getQueryTimeoutMillis();
        String appName = properties.getAppName();
        HotKeyDetector detector = new HotKeyDetector(
                remotingClient,
                hotKeySet,
                appName,
                reportPeriodMillis,
                queryPeriodMillis,
                queryTimeoutMillis
        );
        detector.start();
        return detector;
    }

    private List<InetSocketAddress> buildServerAddresses(HotKeyProperties.ClientConfig clientConfig) {
        List<String> addresses = clientConfig.getServerAddresses();
        if (CollUtil.isEmpty(addresses)) {
            return Collections.singletonList(new InetSocketAddress("127.0.0.1", 8888));
        }
        List<InetSocketAddress> result = new ArrayList<>();
        for (String addr : addresses) {
            if (addr == null || addr.isEmpty()) {
                continue;
            }
            String[] parts = addr.split(":");
            if (parts.length != 2) {
                continue;
            }
            String host = parts[0].trim();
            String portPart = parts[1].trim();
            if (host.isEmpty()) {
                continue;
            }
            try {
                int port = Integer.parseInt(portPart);
                result.add(new InetSocketAddress(host, port));
            } catch (NumberFormatException ignored) {
            }
        }
        if (result.isEmpty()) {
            return Collections.singletonList(new InetSocketAddress("127.0.0.1", 8888));
        }
        return result;
    }

    /**
     * 注册默认的 HotKeyClient。
     *
     * @param detector         热 key 探测器
     * @param hotKeySet        热 key 视图
     * @param postLoadAction   后置处理回调
     * @return 默认 HotKeyClient 实例
     */
    @Bean(name = HotKeyBeanNames.DEFAULT_CLIENT)
    @ConditionalOnMissingBean
    public HotKeyClient hkcacheHotKeyClient(HotKeyDetector detector,
                                            HotKeySet hotKeySet,
                                            PostLoadAction postLoadAction,
                                            CacheTemplate cacheTemplate) {
        HotKeyClientFactory factory = new HotKeyClientFactory();
        //        hotKeyClientManager.register("default", client);
        return factory.createHotKeyClient(detector, hotKeySet, cacheTemplate, postLoadAction);
    }

    private Class<?> resolveClass(String className, Class<?> defaultClass, Class<?> expectedType) {
        if (StringUtils.isEmpty(className)) {
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
}
