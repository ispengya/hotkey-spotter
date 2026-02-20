package com.ispengya.hkcache.server.core;

import com.ispengya.hkcache.server.model.AccessReport;
import com.ispengya.hkcache.server.scheduler.HotKeyChangePublisher;
import com.ispengya.hkcache.server.scheduler.HotKeyComputeTask;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public final class AccessReportPipeline {

    private static final int DEFAULT_QUEUE_CAPACITY = 2_000_000;

    private final InstanceWindowRegistry windowRegistry;
    private final HotKeyComputeAlgorithm algorithm;
    private final HotKeyResultStore resultStore;
    private final HotKeyChangePublisher changePublisher;

    private final BlockingQueue<AccessReport> queue;
    private final ExecutorService consumerExecutor;

    public AccessReportPipeline(InstanceWindowRegistry windowRegistry,
                                HotKeyComputeAlgorithm algorithm,
                                HotKeyResultStore resultStore,
                                HotKeyChangePublisher changePublisher) {
        this.windowRegistry = windowRegistry;
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
                String owner = report.getAppName();
                windowRegistry.selectWindowForApp(owner).addReport(report);
                HotKeyComputeTask.computeAndPublish(
                        report.getAppName(),
                        report.getKey(),
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
