package com.ispengya.hotkey.remoting.server;

import com.ispengya.hotkey.common.nacos.NacosClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 服务注册器。
 *
 * <p>负责将 Worker 服务注册到 nacos，并在关闭时注销。</p>
 *
 * @author ispengya
 */
public final class ServiceRegistration {

    private static final Logger log = LoggerFactory.getLogger(ServiceRegistration.class);

    private final NacosClient nacosClient;
    private final String serviceName;
    private final String ip;
    private final int port;

    public ServiceRegistration(NacosClient nacosClient,
                               String serviceName,
                               String ip,
                               int port) {
        this.nacosClient = nacosClient;
        this.serviceName = serviceName;
        this.ip = ip;
        this.port = port;
    }

    /**
     * 注册服务到 nacos（非致命，失败不影响核心功能）。
     */
    public void register() {
        try {
            nacosClient.registerInstance(serviceName, ip, port);
            log.info("Registered to nacos: {}:{}:{}", serviceName, ip, port);
        } catch (Exception e) {
            log.warn("Failed to register to nacos (non-fatal): {}:{}:{}, error: {}",
                    serviceName, ip, port, e.getMessage());
        }
    }

    /**
     * 从 nacos 注销服务。
     */
    public void deregister() {
        try {
            nacosClient.deregisterInstance(serviceName, ip, port);
            log.info("Deregistered from nacos: {}:{}:{}", serviceName, ip, port);
        } catch (Exception e) {
            log.error("Failed to deregister from nacos", e);
        }
    }
}
