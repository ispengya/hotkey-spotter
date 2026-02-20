package com.ispengya.hkcache.server.core;

import com.ispengya.hkcache.server.model.HotKeyResult;

/**
 * HotKeyResultStore 定义热 Key 结果的存储接口。
 *
 * <p>计算模块将结果写入此处，查询模块从此处读取结果。</p>
 *
 * @author ispengya
 */
public interface HotKeyResultStore {

    /**
     * 更新指定实例的热 Key 结果。
     *
     * @param result 热 Key 结果对象
     */
    void update(HotKeyResult result);

    HotKeyResult get(String appName);

    Iterable<HotKeyResult> listAll();
}
