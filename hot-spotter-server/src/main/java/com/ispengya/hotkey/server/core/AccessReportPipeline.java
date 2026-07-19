package com.ispengya.hotkey.server.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ispengya.hotkey.server.model.AccessReport;
import com.ispengya.hotkey.server.scheduler.HotKeyChangePublisher;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public final class AccessReportPipeline {

    private static final int DEFAULT_QUEUE_CAPACITY = 2_000_000;

    private final AppWindowRegistry windowRegistry;
    private final RuleManager ruleManager;
    private final HotKeyChangePublisher changePublisher;

    /**
     * 推送去重缓存：已推送过的 key 在 duration 秒内不会重复推送。
     * key = "appName:key"，value = 1。
     */
    private final Cache<String, Boolean> pushDedupCache;

    private final BlockingQueue<AccessReport> queue;
    private final ExecutorService consumerExecutor;

    public AccessReportPipeline(AppWindowRegistry windowRegistry,
                                RuleManager ruleManager,
                                HotKeyChangePublisher changePublisher) {
        this.windowRegistry = windowRegistry;
        this.ruleManager = ruleManager;
        this.changePublisher = changePublisher;
        this.pushDedupCache = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterWrite(Duration.ofMinutes(1))
                .build();
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

                // 只对匹配规则的 key 进行统计和推送
                if (!ruleManager.isInRule(appName, key)) {
                    continue;
                }

                SlidingWindow window = windowRegistry.getOrCreateWindow(appName, key);
                boolean hot = window.addCount(report.getCount());

                if (hot) {
                    // 弹出窗口，停止继续计数
                    windowRegistry.removeWindow(appName, key);

                    // 去重：已推送过的 key 短时间内不重复推送
                    String dedupKey = appName + ":" + key;
                    if (pushDedupCache.getIfPresent(dedupKey) != null) {
                        continue;
                    }
                    pushDedupCache.put(dedupKey, true);

                    changePublisher.publish(appName, key);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable ignored) {
            }
        }
    }
}
