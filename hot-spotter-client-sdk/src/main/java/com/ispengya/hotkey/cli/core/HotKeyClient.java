package com.ispengya.hotkey.cli.core;

import com.ispengya.hotkey.cli.detect.HotKeyDetector;
import com.ispengya.hotkey.cli.detect.HotKeySet;

/**
 * HotKeyClient 是对业务方暴露的统一访问入口，
 * 负责串联热 key 探测、本地缓存和回源等能力。
 *
 * 业务只需要提供 ValueLoader 回源逻辑，即可通过 get 方法完成访问。
 *
 * @author ispengya
 */
public class HotKeyClient {

    private final HotKeyDetector hotKeyDetector;
    private final HotKeySet hotKeySet;
    private final CacheTemplate cacheTemplate;
    private final PostLoadAction postLoadAction;

    /**
     * 使用必要依赖构造 HotKeyClient。
     *
     * @param hotKeyDetector  热 key 探测器
     * @param hotKeySet       本地热 key 视图
     * @param cacheTemplate   缓存模板
     * @param postLoadAction  加载完成后的回调
     */
    public HotKeyClient(HotKeyDetector hotKeyDetector,
                        HotKeySet hotKeySet,
                        CacheTemplate cacheTemplate,
                        PostLoadAction postLoadAction) {
        this.hotKeyDetector = hotKeyDetector;
        this.hotKeySet = hotKeySet;
        this.cacheTemplate = cacheTemplate;
        this.postLoadAction = postLoadAction;
    }

    /**
     * 统一访问入口：记录访问、判断是否热 key、走缓存模板并上报结果。
     *
     * @param key    业务 key
     * @param loader 回源逻辑
     * @param <T>    返回值类型
     * @return 业务数据结果
     */
    public <T> T get(String key, ValueLoader<T> loader) {
        long startNs = System.nanoTime();

        boolean isHot = hotKeySet.contains(key);

        if (!isHot) {
            hotKeyDetector.recordAccess(key);
        }

        CacheableContext context = new CacheableContext(key, isHot, System.currentTimeMillis());

        CacheResult<T> result = cacheTemplate.load(context, loader);

        postLoadAction.onLoaded(key, result.getValue(), result.isFromCache(), result.getCostNanos());

        return result.getValue();
    }

    /**
     * 失效当前实例下指定 key 的本地缓存。
     *
     * @param key 需要失效的业务 key
     */
    public void evict(String key) {
        CacheEvictContext context = new CacheEvictContext(
                key,
                System.currentTimeMillis()
        );
        cacheTemplate.evict(context);
    }

    public void evictAll() {
        cacheTemplate.evictAll();
    }
}
