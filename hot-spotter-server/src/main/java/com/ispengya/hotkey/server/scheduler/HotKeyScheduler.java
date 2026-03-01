package com.ispengya.hotkey.server.scheduler;

import com.ispengya.hotkey.server.core.HotKeyComputeAlgorithm;
import com.ispengya.hotkey.server.core.HotKeyResultStore;
import com.ispengya.hotkey.server.core.InstanceWindowRegistry;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * HotKeyScheduler 聚合调度器。
 *
 * <p>定期触发热 Key 计算任务，遍历所有活跃实例并提交 {@link HotKeyComputeTask} 到工作线程池。</p>
 *
 * @author ispengya
 */
public final class HotKeyScheduler {

    private final ScheduledExecutorService scheduler;
    private final ExecutorService workerPool;
    private final InstanceWindowRegistry aggregator;
    private final HotKeyComputeAlgorithm algorithm;
    private final HotKeyResultStore resultStore;
    private final long periodMillis;
    private final long decayPeriodMillis;
    private final long hotKeyIdleMillis;
    private final HotKeyChangePublisher changePublisher;
    private final boolean debugEnabled;

    /**
     * 构造调度器。
     *
     * @param scheduler   定时调度线程池
     * @param workerPool  计算工作线程池
     * @param aggregator  聚合器
     * @param algorithm   计算算法
     * @param resultStore 结果存储
     * @param periodMillis 调度周期（毫秒）
     */
    public HotKeyScheduler(ScheduledExecutorService scheduler,
                           ExecutorService workerPool,
                           InstanceWindowRegistry aggregator,
                           HotKeyComputeAlgorithm algorithm,
                           HotKeyResultStore resultStore,
                           long periodMillis,
                           HotKeyChangePublisher changePublisher,
                           long decayPeriodMillis,
                           long hotKeyIdleMillis,
                           boolean debugEnabled) {
        this.scheduler = scheduler;
        this.workerPool = workerPool;
        this.aggregator = aggregator;
        this.algorithm = algorithm;
        this.resultStore = resultStore;
        this.periodMillis = periodMillis;
        this.decayPeriodMillis = decayPeriodMillis;
        this.hotKeyIdleMillis = hotKeyIdleMillis;
        this.changePublisher = changePublisher;
        this.debugEnabled = debugEnabled;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(
                this::scheduleDecayTasks,
                decayPeriodMillis,
                decayPeriodMillis,
                TimeUnit.MILLISECONDS
        );
    }

    public void stop() {
        scheduler.shutdown();
        workerPool.shutdown();
    }

    private void scheduleDecayTasks() {
        for (String appName : aggregator.listAppNames()) {
            HotKeyDecayTask task = new HotKeyDecayTask(
                    appName,
                    resultStore,
                    hotKeyIdleMillis,
                    changePublisher,
                    debugEnabled
            );
            workerPool.submit(task);
        }
    }
}
