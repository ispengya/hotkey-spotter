package com.ispengya.hotkey.cli.detect;

import com.ispengya.hotkey.remoting.client.HotKeyRemotingClient;
import com.ispengya.hotkey.remoting.message.AccessReportMessage;
import com.ispengya.hotkey.remoting.message.HotKeyViewMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * HotKeyDetector 负责采集访问轨迹和上报访问统计，
 * 并委托 HotKeyViewRefresher 同步最新的热 Key 视图。
 *
 * <p>该类不直接感知底层通信协议，聚焦在采样和上报行为。</p>
 *
 * @author ispengya
 */
public class HotKeyDetector {

    private static final Logger log = LoggerFactory.getLogger(HotKeyDetector.class);

    private final ScheduledExecutorService scheduler;
    private final HotKeyTransport transport;
    private final HotKeySet hotKeySet;
    private final AccessCounterCollector collector = new AccessCounterCollector();
    private final long reportPeriodMillis;

    /**
     * 构造 HotKeyDetector。
     *
     * @param remotingClient 底层 Remoting 客户端
     * @param hotKeySet      本地热 Key 视图
     */
    public HotKeyDetector(HotKeyRemotingClient remotingClient,
                          HotKeySet hotKeySet,
                          String appName,
                          long reportPeriodMillis,
                          long queryPeriodMillis,
                          long queryTimeoutMillis) {
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.transport = new HotKeyTransport(remotingClient, appName);
        this.reportPeriodMillis = reportPeriodMillis;
        this.hotKeySet = hotKeySet;
        this.transport.setPushListener(this::handlePush);
    }

    public HotKeyDetector(HotKeyRemotingClient remotingClient,
                          HotKeySet hotKeySet,
                          String appName) {
        this(remotingClient, hotKeySet, appName, 500L, 30000L, 3000L);
    }

    /**
     * 启动探测器，包括定时上报任务和视图刷新任务。
     */
    public void start() {
        scheduler.scheduleAtFixedRate(
                this::reportTask,
                reportPeriodMillis,
                reportPeriodMillis,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * 停止所有定时任务。
     */
    public void stop() {
        scheduler.shutdown();
    }

    /**
     * 记录一次访问事件，累加到内存缓冲中。
     *
     * @param key 业务 Key
     */
    public void recordAccess(String key) {
        if (key == null) {
            return;
        }
        collector.record(key);
    }

    /**
     * 定时执行访问统计上报任务。
     */
    private void reportTask() {
        try {
            Map<String, Integer> counts = collector.drain();
            if (counts.isEmpty()) {
                return;
            }

            long timestamp = System.currentTimeMillis();
            AccessReportMessage message = new AccessReportMessage();
            message.setTimestamp(timestamp);
            message.setKeyAccessCounts(counts);
            transport.reportAccess(message);
        } catch (Exception e) {
            log.error("Failed to report access data", e);
        }
    }

    private void handlePush(HotKeyViewMessage message) {
        if (message == null) {
            return;
        }
        handleMessage(message);
    }

    private void handleMessage(HotKeyViewMessage message) {
        if (message == null) {
            return;
        }
        Map<String, HotKeyViewMessage.ViewEntry> views = message.getViews();
        if (views == null || views.isEmpty()) {
            return;
        }
        views.forEach((appName, entry) -> {
            if (entry == null) {
                return;
            }
            boolean updated = false;
            if (entry.getAddedKey() != null) {
                updated |= hotKeySet.add(entry.getAddedKey());
            }
            if (entry.getRemovedKey() != null) {
                updated |= hotKeySet.remove(entry.getRemovedKey());
            }
            if (updated && log.isDebugEnabled()) {
                log.debug("Updated hot keys for app {}", appName);
            }
        });
    }

    static final class AccessCounterCollector {

        private final ConcurrentHashMap<String, LongAdder> map0 = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, LongAdder> map1 = new ConcurrentHashMap<>();
        private final AtomicLong seq = new AtomicLong();

        void record(String key) {
            if (key == null) {
                return;
            }
            if ((seq.get() & 1L) == 0L) {
                map0.computeIfAbsent(key, k -> new LongAdder()).increment();
            } else {
                map1.computeIfAbsent(key, k -> new LongAdder()).increment();
            }
        }

        Map<String, Integer> drain() {
            long v = seq.incrementAndGet();
            ConcurrentHashMap<String, LongAdder> target = (v & 1L) == 0L ? map1 : map0;
            Map<String, Integer> result = new HashMap<>();
            target.forEach((key, adder) -> {
                long count = adder.sumThenReset();
                if (count > 0L) {
                    result.put(key, (int) count);
                }
            });
            target.entrySet().removeIf(e -> e.getValue().sum() == 0L);
            return result;
        }
    }
}
