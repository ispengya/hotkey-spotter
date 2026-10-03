package com.ispengya.hotkey.cli.detect;

import com.ispengya.hotkey.cli.rule.RuleHolder;
import com.ispengya.hotkey.remoting.client.HotKeyRemotingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * HotKeyReporter 负责采集访问轨迹和定时批量上报给 Worker。
 *
 * @author ispengya
 */
public class HotKeyReporter {

    private static final Logger log = LoggerFactory.getLogger(HotKeyReporter.class);

    private final ScheduledExecutorService scheduler;
    private final HotKeyRemotingClient remotingClient;
    private final String appName;
    private final AccessCounterCollector collector;
    private final long reportPeriodMillis;

    public HotKeyReporter(HotKeyRemotingClient remotingClient,
                          String appName,
                          long reportPeriodMillis) {
        this(remotingClient, appName, reportPeriodMillis, CounterMode.DOUBLE);
    }

    public HotKeyReporter(HotKeyRemotingClient remotingClient,
                          String appName,
                          long reportPeriodMillis,
                          CounterMode counterMode) {
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.remotingClient = remotingClient;
        this.appName = appName;
        this.reportPeriodMillis = reportPeriodMillis;
        this.collector = createCollector(counterMode);

        log.info("HotKeyReporter initialized. appName={}, reportPeriodMillis={}, counterMode={}",
                appName, reportPeriodMillis, counterMode);
    }

    public HotKeyReporter(HotKeyRemotingClient remotingClient, String appName) {
        this(remotingClient, appName, 500L);
    }

    private static AccessCounterCollector createCollector(CounterMode mode) {
        if (mode == CounterMode.LOCK) {
            return new LockedCounterCollector();
        }
        return new DoubleBufferCounterCollector();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(
                this::report,
                reportPeriodMillis,
                reportPeriodMillis,
                TimeUnit.MILLISECONDS
        );
        log.info("HotKeyReporter started. reportPeriodMillis={}", reportPeriodMillis);
    }

    public void stop() {
        scheduler.shutdown();
        log.info("HotKeyReporter stopped.");
    }

    public void record(String key) {
        if (key == null) {
            return;
        }
        // 只有命中规则的 key 才上报，减少无效网络开销和 Worker 算力消耗
        if (!RuleHolder.isInRule(appName, key)) {
            return;
        }
        collector.record(key);
    }

    private void report() {
        try {
            Map<String, Integer> counts = collector.drain();
            if (counts.isEmpty()) {
                return;
            }

            long timestamp = System.currentTimeMillis();
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                // 每个 key 单独上报，hash 路由保证同一 key 落到同一台 Worker
                remotingClient.reportAccess(appName, entry.getKey(), entry.getValue(), timestamp);
            }
            log.debug("Reported access data. size={}", counts.size());
        } catch (Exception e) {
            log.error("Failed to report access data", e);
        }
    }
}
