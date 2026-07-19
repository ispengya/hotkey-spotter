package com.ispengya.hotkey.server.core;

import com.ispengya.hotkey.server.model.AccessReport;
import com.ispengya.hotkey.server.scheduler.HotKeyChangePublisher;
import com.ispengya.hotkey.server.scheduler.HotKeyComputeTask;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public final class AccessReportPipeline {

    private static final int DEFAULT_QUEUE_CAPACITY = 2_000_000;

    private final InstanceWindowRegistry windowRegistry;
    private final RuleManager ruleManager;
    private final HotKeyComputeAlgorithm algorithm;
    private final HotKeyResultStore resultStore;
    private final HotKeyChangePublisher changePublisher;

    private final BlockingQueue<AccessReport> queue;
    private final ExecutorService consumerExecutor;

    public AccessReportPipeline(InstanceWindowRegistry windowRegistry,
                                RuleManager ruleManager,
                                HotKeyComputeAlgorithm algorithm,
                                HotKeyResultStore resultStore,
                                HotKeyChangePublisher changePublisher) {
        this.windowRegistry = windowRegistry;
        this.ruleManager = ruleManager;
        this.algorithm = algorithm;
        this.resultStore = resultStore;
        this.changePublisher = changePublisher;
        this.queue = new LinkedBlockingQueue<>(DEFAULT_QUEUE_CAPACITY);
        this.consumerExecutor = Executors.newFixedThreadPool(determineConsumerCount());
        startConsumers();
    }

    public void submit(AccessReport report) {
        if (report == null || report.getKey() == null || report.getAppName() == null) {
            return;
        }
        try {
            queue.put(report);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void startConsumers() {
        int nThreads = determineConsumerCount();
        for (int i = 0; i < nThreads; i++) {
            consumerExecutor.submit(this::consumeLoop);
        }
    }

    private int determineConsumerCount() {
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores <= 0) {
            return 1;
        }
        int half = cores / 2;
        return half > 0 ? half : 1;
    }

    private void consumeLoop() {
        while (true) {
            try {
                AccessReport report = queue.take();
                String appName = report.getAppName();
                String key = report.getKey();

                // 只对匹配规则的 key 进行统计和计算
                if (!ruleManager.isInRule(appName, key)) {
                    continue;
                }

                windowRegistry.selectWindowForApp(appName).addReport(report);
                HotKeyComputeTask.computeAndPublish(
                        appName,
                        key,
                        windowRegistry,
                        algorithm,
                        resultStore,
                        changePublisher
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable ignored) {
            }
        }
    }
}
