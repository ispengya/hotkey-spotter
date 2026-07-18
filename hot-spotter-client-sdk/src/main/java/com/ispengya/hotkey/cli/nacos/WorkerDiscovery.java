package com.ispengya.hotkey.cli.nacos;

import com.alibaba.nacos.api.naming.pojo.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Worker 节点发现。
 *
 * <p>从 nacos 获取 Worker 节点列表，并监听节点变化。</p>
 *
 * @author ispengya
 */
public final class WorkerDiscovery {

    private static final Logger log = LoggerFactory.getLogger(WorkerDiscovery.class);

    private final NacosConfigClient nacosClient;
    private final String serviceName;
    private final CopyOnWriteArrayList<InetSocketAddress> workerAddresses = new CopyOnWriteArrayList<>();
    private volatile Consumer<List<InetSocketAddress>> addressChangeListener;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "worker-discovery-scheduler");
        t.setDaemon(true);
        return t;
    });

    public WorkerDiscovery(NacosConfigClient nacosClient, String serviceName) {
        this.nacosClient = nacosClient;
        this.serviceName = serviceName;
    }

    /**
     * 设置地址变更监听器。
     */
    public void setAddressChangeListener(Consumer<List<InetSocketAddress>> listener) {
        this.addressChangeListener = listener;
    }

    /**
     * 初始化：加载 Worker 列表并订阅变更。
     */
    public void init() {
        // 初始加载
        refreshWorkerAddresses();

        // 订阅变更
        try {
            nacosClient.subscribe(serviceName, event -> {
                log.info("Worker list changed: {}", event);
                refreshWorkerAddresses();
            });
        } catch (Exception e) {
            log.error("Failed to subscribe to service: {}", serviceName, e);
        }

        // 定时刷新（兜底）
        scheduler.scheduleAtFixedRate(this::refreshWorkerAddresses, 30, 30, TimeUnit.SECONDS);

        log.info("WorkerDiscovery initialized, serviceName={}, workers={}", serviceName, workerAddresses);
    }

    /**
     * 刷新 Worker 地址列表。
     */
    private void refreshWorkerAddresses() {
        try {
            List<Instance> instances = nacosClient.getAllInstances(serviceName);
            List<InetSocketAddress> newAddresses = new ArrayList<>();
            for (Instance instance : instances) {
                if (instance.isEnabled() && instance.isHealthy()) {
                    newAddresses.add(new InetSocketAddress(instance.getIp(), instance.getPort()));
                }
            }

            // 更新列表
            workerAddresses.clear();
            workerAddresses.addAll(newAddresses);

            // 通知监听器
            Consumer<List<InetSocketAddress>> listener = addressChangeListener;
            if (listener != null) {
                listener.accept(new ArrayList<>(workerAddresses));
            }

            log.info("Refreshed worker addresses: {}", workerAddresses);
        } catch (Exception e) {
            log.error("Failed to refresh worker addresses", e);
        }
    }

    /**
     * 获取当前 Worker 地址列表。
     */
    public List<InetSocketAddress> getWorkerAddresses() {
        return new ArrayList<>(workerAddresses);
    }

    /**
     * 关闭。
     */
    public void shutdown() {
        scheduler.shutdown();
    }
}
