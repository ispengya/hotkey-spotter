package com.ispengya.hotkey.cli.detect;

import com.ispengya.hotkey.common.event.EventBus;
import com.ispengya.hotkey.remoting.event.HotKeyPushEvent;
import com.ispengya.hotkey.remoting.client.HotKeyRemotingClient;
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
 * HotKeyReporter 负责采集访问轨迹和上报访问统计，
 * 并处理来自 Server 的热 Key 推送。
 *
 * <p>通过 EventBus 监听 HotKeyPushEvent，不再使用 Consumer 回调。</p>
 *
 * @author ispengya
 */
public class HotKeyReporter {

    private static final Logger log = LoggerFactory.getLogger(HotKeyReporter.class);

    private final ScheduledExecutorService scheduler;
    private final HotKeyRemotingClient remotingClient;
    private final String appName;
    private final AccessCounterCollector collector = new AccessCounterCollector();
    private final long reportPeriodMillis;
    private HotKeyListener hotKeyListener;

    /**
     * 热 key 监听器接口。
     */
    public interface HotKeyListener {
        void onHotKeyAdded(String key, int durationSeconds);
        void onHotKeyRemoved(String key);
    }

    public HotKeyReporter(HotKeyRemotingClient remotingClient,
                          String appName,
                          long reportPeriodMillis,
                          long queryPeriodMillis,
                          long queryTimeoutMillis,
                          EventBus eventBus) {
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.remotingClient = remotingClient;
        this.appName = appName;
        this.reportPeriodMillis = reportPeriodMillis;

        // 监听 EventBus 上的热 Key 推送
        eventBus.register(HotKeyPushEvent.class, event -> {
            handlePush(event.getMessage());
        });

        log.info("HotKeyReporter initialized. appName={}, reportPeriodMillis={}", appName, reportPeriodMillis);
    }

    public HotKeyReporter(HotKeyRemotingClient remotingClient, String appName, EventBus eventBus) {
        this(remotingClient, appName, 500L, 30000L, 3000L, eventBus);
    }

    public void setHotKeyListener(HotKeyListener listener) {
        this.hotKeyListener = listener;
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

    private void handlePush(HotKeyViewMessage message) {
        if (message == null) {
            return;
        }
        Map<String, HotKeyViewMessage.ViewEntry> views = message.getViews();
        if (views == null || views.isEmpty()) {
            return;
        }
        views.forEach((appName, entry) -> {
            if (entry == null || hotKeyListener == null) {
                return;
            }
            if (entry.getAddedKey() != null) {
                int durationSeconds = entry.getDuration();
                hotKeyListener.onHotKeyAdded(entry.getAddedKey(), durationSeconds);
                log.debug("Hot key added: key={}, durationSeconds={}", entry.getAddedKey(), durationSeconds);
            }
            if (entry.getRemovedKey() != null) {
                hotKeyListener.onHotKeyRemoved(entry.getRemovedKey());
                log.debug("Hot key removed: key={}", entry.getRemovedKey());
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
