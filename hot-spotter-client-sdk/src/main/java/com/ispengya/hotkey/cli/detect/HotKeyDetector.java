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
 * 并处理来自 Server 的热 Key 推送。
 *
 * <p>该类不直接感知底层通信协议，聚焦在采样和上报行为。</p>
 *
 * @author ispengya
 */
public class HotKeyDetector {

    private static final Logger log = LoggerFactory.getLogger(HotKeyDetector.class);

    private final ScheduledExecutorService scheduler;
    private final HotKeyTransport transport;
    private final AccessCounterCollector collector = new AccessCounterCollector();
    private final long reportPeriodMillis;
    private HotKeyListener hotKeyListener;

    /**
     * 热 key 监听器接口。
     */
    public interface HotKeyListener {
        /**
         * 当收到热 key 添加事件时调用。
         *
         * @param key             热 key
         * @param durationSeconds 过期时长（秒）
         */
        void onHotKeyAdded(String key, int durationSeconds);

        /**
         * 当收到热 key 删除事件时调用。
         *
         * @param key 热 key
         */
        void onHotKeyRemoved(String key);
    }

    /**
     * 构造 HotKeyDetector。
     *
     * @param remotingClient 底层 Remoting 客户端
     * @param appName        应用名称
     */
    public HotKeyDetector(HotKeyRemotingClient remotingClient,
                          String appName,
                          long reportPeriodMillis,
                          long queryPeriodMillis,
                          long queryTimeoutMillis) {
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.transport = new HotKeyTransport(remotingClient, appName);
        this.reportPeriodMillis = reportPeriodMillis;
        this.transport.setPushListener(this::handlePush);
        if (log.isInfoEnabled()) {
            log.info("HotKeyDetector initialized. appName={}, reportPeriodMillis={}",
                    appName, reportPeriodMillis);
        }
    }

    public HotKeyDetector(HotKeyRemotingClient remotingClient, String appName) {
        this(remotingClient, appName, 500L, 30000L, 3000L);
    }

    /**
     * 设置热 key 监听器。
     *
     * @param listener 热 key 监听器
     */
    public void setHotKeyListener(HotKeyListener listener) {
        this.hotKeyListener = listener;
    }

    /**
     * 启动探测器，包括定时上报任务。
     */
    public void start() {
        scheduler.scheduleAtFixedRate(
                this::reportTask,
                reportPeriodMillis,
                reportPeriodMillis,
                TimeUnit.MILLISECONDS
        );
        if (log.isInfoEnabled()) {
            log.info("HotKeyDetector started. reportPeriodMillis={}", reportPeriodMillis);
        }
    }

    /**
     * 停止所有定时任务。
     */
    public void stop() {
        scheduler.shutdown();
        if (log.isInfoEnabled()) {
            log.info("HotKeyDetector stopped.");
        }
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
            if (log.isInfoEnabled()) {
                log.info("Reported access data. size={}", counts.size());
            }
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
            if (hotKeyListener == null) {
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
