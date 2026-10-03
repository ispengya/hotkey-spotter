package com.ispengya.hotkey.server.monitor;

import com.ispengya.hotkey.common.nacos.NacosClient;
import com.ispengya.hotkey.server.core.AccessReportPipeline;
import com.ispengya.hotkey.server.core.AppWindowRegistry;
import com.ispengya.hotkey.remoting.server.ServerChannelManager;
import com.ispengya.hotkey.server.scheduler.HotKeyChangePublisher;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 监控指标上报器。
 *
 * <p>每 {@code reportPeriodSeconds} 秒采集一次指标，
 * 包含实时 QPS 速率、平均延迟、队列积压等，序列化为 JSON 推送到 Nacos。</p>
 *
 * <h3>上报内容分组</h3>
 * <ul>
 *   <li><b>累计值</b> — RECEIVED/OFFERED/DEALT/HOT_KEY_DETECTED/PUSHED/PUSH_FAILED</li>
 *   <li><b>QPS 速率</b> — 用本次快照与上次快照的差值除以间隔时间计算</li>
 *   <li><b>延迟</b> — Pipeline 排队延迟、addCount 锁耗时，取平均值</li>
 *   <li><b>队列</b> — Pipeline 队列和推送队列的快照大小</li>
 *   <li><b>资源</b> — channelCount、windowCacheSize</li>
 * </ul>
 *
 * @author ispengya
 */
public final class MonitorReporter {

    private static final Logger log = LoggerFactory.getLogger(MonitorReporter.class);

    private static final int DEFAULT_REPORT_PERIOD_SECONDS = 10;

    private final NacosClient nacosClient;
    private final String dataId;
    private final String ip;
    private final String group;
    private final ScheduledExecutorService scheduler;

    private AccessReportPipeline pipeline;
    private HotKeyChangePublisher publisher;
    private ServerChannelManager channelManager;
    private AppWindowRegistry windowRegistry;

    // ==================== JMX Beans ====================

    private final MemoryMXBean memoryBean;
    private final OperatingSystemMXBean osBean;
    private final ThreadMXBean threadBean;
    private final List<GarbageCollectorMXBean> gcBeans;

    // ==================== 上一次快照（用于计算 QPS 速率） ====================

    private long prevReceived;
    private long prevOffered;
    private long prevDealt;
    private long prevHotKeyDetected;
    private long prevPushed;
    private long prevPushFailed;

    public MonitorReporter(NacosClient nacosClient, String dataId, int workerPort) {
        this.nacosClient = nacosClient;
        this.dataId = dataId;
        this.ip = getLocalIp();
        this.group = "worker-" + ip + ":" + workerPort;
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        // 触发一次 CPU 测量，下次调用 getProcessCpuLoad() 才能拿到有效值
        this.osBean.getSystemLoadAverage();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "monitor-reporter");
            t.setDaemon(true);
            return t;
        });
    }

    // ==================== 注入依赖 ====================

    public void setPipeline(AccessReportPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public void setPublisher(HotKeyChangePublisher publisher) {
        this.publisher = publisher;
    }

    public void setChannelManager(ServerChannelManager channelManager) {
        this.channelManager = channelManager;
    }

    public void setWindowRegistry(AppWindowRegistry windowRegistry) {
        this.windowRegistry = windowRegistry;
    }

    // ==================== 生命周期 ====================

    public void start() {
        scheduler.scheduleAtFixedRate(this::report,
                DEFAULT_REPORT_PERIOD_SECONDS,
                DEFAULT_REPORT_PERIOD_SECONDS,
                TimeUnit.SECONDS);
        log.info("MonitorReporter started. dataId={}, group={}, period={}s",
                dataId, group, DEFAULT_REPORT_PERIOD_SECONDS);
    }

    public void stop() {
        scheduler.shutdown();
        log.info("MonitorReporter stopped.");
    }

    // ==================== 采集上报 ====================

    private void report() {
        try {
            // —— 读取当前累计快照 ——
            long curReceived = MonitorMetrics.RECEIVED.sum();
            long curOffered = MonitorMetrics.OFFERED.sum();
            long curDealt = MonitorMetrics.DEALT.sum();
            long curHotKeyDetected = MonitorMetrics.HOT_KEY_DETECTED.sum();
            long curPushed = MonitorMetrics.PUSHED.sum();
            long curPushFailed = MonitorMetrics.PUSH_FAILED.sum();

            long curLatencySum = MonitorMetrics.PIPELINE_LATENCY_TOTAL_MS.sum();
            long curLatencyCount = MonitorMetrics.PIPELINE_LATENCY_COUNT.sum();
            long curAddCountNsSum = MonitorMetrics.ADD_COUNT_TOTAL_NS.sum();
            long curAddCountNsCount = MonitorMetrics.ADD_COUNT_COUNT.sum();

            // —— 计算 QPS（累计差值 / 间隔秒数） ——
            double receivedQps = (curReceived - prevReceived) / (double) DEFAULT_REPORT_PERIOD_SECONDS;
            double offeredQps = (curOffered - prevOffered) / (double) DEFAULT_REPORT_PERIOD_SECONDS;
            double dealtQps = (curDealt - prevDealt) / (double) DEFAULT_REPORT_PERIOD_SECONDS;
            double hotKeyDetectedQps = (curHotKeyDetected - prevHotKeyDetected) / (double) DEFAULT_REPORT_PERIOD_SECONDS;
            double pushedQps = (curPushed - prevPushed) / (double) DEFAULT_REPORT_PERIOD_SECONDS;

            // —— 保存快照 ——
            prevReceived = curReceived;
            prevOffered = curOffered;
            prevDealt = curDealt;
            prevHotKeyDetected = curHotKeyDetected;
            prevPushed = curPushed;
            prevPushFailed = curPushFailed;

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("workerIp", ip);
            metrics.put("timestamp", System.currentTimeMillis());
            metrics.put("intervalSeconds", DEFAULT_REPORT_PERIOD_SECONDS);

            // ========== QPS 速率（核心，压测必看） ==========
            metrics.put("receivedQps", fmt(receivedQps));
            metrics.put("offeredQps", fmt(offeredQps));
            metrics.put("dealtQps", fmt(dealtQps));

            // 流水线健康标识
            metrics.put("pipelineHealth", judgeHealth(receivedQps, offeredQps, dealtQps));

            // ========== 累计值 ==========
            metrics.put("received", curReceived);
            metrics.put("offered", curOffered);
            metrics.put("dealt", curDealt);

            // ========== 队列积压（快照） ==========
            metrics.put("pipelineQueueSize", pipeline != null ? pipeline.getQueueSize() : -1);
            metrics.put("pushQueueSize", publisher != null ? publisher.getQueueSize() : -1);

            // ========== 热 Key 判定 & 推送 ==========
            metrics.put("hotKeyDetected", curHotKeyDetected);
            metrics.put("hotKeyDetectedQps", fmt(hotKeyDetectedQps));
            metrics.put("pushed", curPushed);
            metrics.put("pushedQps", fmt(pushedQps));
            metrics.put("pushFailed", curPushFailed);

            // 推送成功率
            long totalPush = curPushed + curPushFailed;
            if (totalPush > 0) {
                metrics.put("pushSuccessRate", Math.round(
                        curPushed * 10000.0 / totalPush) / 100.0);
            } else {
                metrics.put("pushSuccessRate", 100.0);
            }

            // ========== 延迟（核心，瓶颈定位） ==========
            if (curLatencyCount > 0) {
                metrics.put("pipelineAvgLatencyMs", fmt(curLatencySum / (double) curLatencyCount));
            } else {
                metrics.put("pipelineAvgLatencyMs", 0.0);
            }
            if (curAddCountNsCount > 0) {
                // 纳秒 → 微秒，更直观
                double avgAddCountUs = curAddCountNsSum / (double) curAddCountNsCount / 1000.0;
                metrics.put("addCountAvgUs", fmt(avgAddCountUs));
            } else {
                metrics.put("addCountAvgUs", 0.0);
            }

            // ========== 连接 & 缓存 ==========
            metrics.put("channelCount", channelManager != null ? channelManager.getChannelCount() : -1);
            metrics.put("windowCacheSize", windowRegistry != null ? windowRegistry.getTotalCacheSize() : -1);

            // ========== JVM 资源 ==========
            collectJvmMetrics(metrics);

            String content = JSON.toJSONString(metrics);
            nacosClient.publishConfig(dataId, group, content);
            log.debug("Monitor metrics pushed: {}", content);
        } catch (Exception e) {
            log.error("Failed to push monitor metrics to Nacos", e);
        }
    }

    /**
     * 判断流水线健康度：receive → offer → deal 三段的吞吐是否一致。
     */
    private static String judgeHealth(double receivedQps, double offeredQps, double dealtQps) {
        if (receivedQps <= 0) {
            return "IDLE";
        }
        // received > offered 说明队列满了，put() 在阻塞
        if (receivedQps > offeredQps * 1.1) {
            return "BLOCKED";
        }
        // offered > dealt 说明消费跟不上入队
        if (offeredQps > dealtQps * 1.1) {
            return "BACKLOG";
        }
        return "HEALTHY";
    }

    /**
     * 采集 JVM 级别资源指标：堆内存、CPU、线程、GC。
     */
    private void collectJvmMetrics(Map<String, Object> metrics) {
        try {
            // —— 堆内存 ——
            MemoryUsage heap = memoryBean.getHeapMemoryUsage();
            long heapUsedMB = heap.getUsed() / (1024 * 1024);
            long heapMaxMB = heap.getMax() / (1024 * 1024);
            metrics.put("heapUsedMB", heapUsedMB);
            metrics.put("heapMaxMB", heapMaxMB);

            // —— CPU ——
            // com.sun.management.OperatingSystemMXBean 提供 getProcessCpuLoad()
            // 标准 OperatingSystemMXBean 是它的父接口，需要反射或类型判断
            double processCpu = -1;
            double systemCpu = -1;
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOs =
                        (com.sun.management.OperatingSystemMXBean) osBean;
                processCpu = sunOs.getProcessCpuLoad();
                systemCpu = sunOs.getSystemCpuLoad();
            }
            metrics.put("processCpuLoad", fmtPercent(processCpu));
            metrics.put("systemCpuLoad", fmtPercent(systemCpu));

            // —— 线程 ——
            metrics.put("threadCount", threadBean.getThreadCount());
            metrics.put("peakThreadCount", threadBean.getPeakThreadCount());
            metrics.put("daemonThreadCount", threadBean.getDaemonThreadCount());

            // —— GC ——
            long totalGcCount = 0;
            long totalGcTimeMs = 0;
            for (GarbageCollectorMXBean gcBean : gcBeans) {
                totalGcCount += gcBean.getCollectionCount();
                totalGcTimeMs += gcBean.getCollectionTime();
            }
            metrics.put("gcCount", totalGcCount);
            metrics.put("gcTimeMs", totalGcTimeMs);

            // —— 运行时长 ——
            long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
            metrics.put("uptimeSeconds", uptimeSeconds);

        } catch (Exception ignored) {
            // JMX 采集失败不影响业务
        }
    }

    /**
     * CPU load 格式化为百分比，-1 表示不可用。
     */
    private static double fmtPercent(double value) {
        if (value < 0) return -1.0;
        return Math.round(value * 1000.0) / 10.0;  // 0.xx → xx.x%
    }

    /**
     * 格式化 double，保留一位小数。
     */
    private static double fmt(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
