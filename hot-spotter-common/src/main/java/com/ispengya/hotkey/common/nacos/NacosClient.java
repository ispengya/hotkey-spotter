package com.ispengya.hotkey.common.nacos;

import com.ispengya.hotkey.common.rule.RuleConfig;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;

import java.util.List;
import java.util.Map;

/**
 * Nacos 客户端接口。
 *
 * <p>定义 nacos 配置中心和服务发现的通用操作。</p>
 *
 * @author ispengya
 */
public interface NacosClient {

    /**
     * 初始化客户端。
     */
    void init() throws NacosException;

    // ==================== 配置操作 ====================

    /**
     * 获取配置内容。
     *
     * @param dataId 配置 ID
     * @param group  配置分组
     * @return 配置内容
     */
    String getConfig(String dataId, String group) throws NacosException;

    // ==================== 服务发现 ====================

    /**
     * 获取服务的所有实例。
     *
     * @param serviceName 服务名
     * @return 实例列表
     */
    List<Instance> getAllInstances(String serviceName) throws NacosException;

    /**
     * 订阅服务变更。
     *
     * @param serviceName 服务名
     * @param listener    变更监听器
     */
    void subscribe(String serviceName, com.alibaba.nacos.api.naming.listener.EventListener listener) throws NacosException;

    /**
     * 取消订阅服务变更。
     *
     * @param serviceName 服务名
     * @param listener    变更监听器
     */
    void unsubscribe(String serviceName, com.alibaba.nacos.api.naming.listener.EventListener listener) throws NacosException;

    /**
     * 注册服务实例。
     */
    void registerInstance(String serviceName, String ip, int port) throws NacosException;

    /**
     * 注销服务实例。
     */
    void deregisterInstance(String serviceName, String ip, int port) throws NacosException;

    // ==================== 规则管理 ====================

    /**
     * 加载指定应用的规则配置。
     *
     * @param dataId 配置 ID
     * @param group  配置分组（应用名称）
     * @return 规则配置
     */
    RuleConfig loadRuleConfig(String dataId, String group);

    /**
     * 监听规则配置变更。
     *
     * @param dataId 配置 ID
     * @param group  配置分组（应用名称）
     */
    void addRuleConfigListener(String dataId, String group);

    /**
     * 获取缓存的规则配置。
     *
     * @param appName 应用名称
     * @return 规则配置
     */
    RuleConfig getCachedRuleConfig(String appName);

    /**
     * 获取所有缓存的规则配置。
     *
     * @return 规则配置映射
     */
    Map<String, RuleConfig> getAllCachedRuleConfigs();

    /**
     * 发布配置到 Nacos。
     *
     * @param dataId  配置 ID
     * @param group   配置分组
     * @param content 配置内容
     */
    void publishConfig(String dataId, String group, String content);

    /**
     * 关闭客户端。
     */
    void shutdown();
}
