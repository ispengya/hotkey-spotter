package com.ispengya.hotkey.cli.core;

/**
 * CacheTemplate 定义缓存与回源的统一模板接口，
 * 负责编排是否使用缓存、如何加载数据以及写回缓存等流程。
 *
 * HotKeyClient 会依赖该模板完成完整的访问逻辑。
 *
 * @author ispengya
 */
public interface CacheTemplate {

    /**
     * 根据当前访问上下文做出简易缓存决策。
     * 默认仅对热 key 使用缓存，不修改本地缓存自身 TTL 配置。
     *
     * 业务方在自定义缓存模板时，可以根据需要重写该方法。
     *
     * @param context 访问上下文
     * @return 缓存决策结果
     */
    CacheDecision decide(CacheableContext context);
    /**
     * 根据上下文和回源逻辑加载数据，内部可以决定是否走缓存。
     *
     * @param context 访问上下文
     * @param loader  业务回源逻辑
     * @param <T>     返回值类型
     * @return 包含结果和元信息的 CacheResult
     */
    <T> CacheResult<T> load(CacheableContext context, ValueLoader<T> loader);

    /**
     * 失效指定 key 的缓存数据。
     *
     * @param context 缓存失效上下文
     */
    void evict(CacheEvictContext context);

    /**
     * 失效某个实例下的全部缓存数据。
     *
     */
    void evictAll();
}
