package com.ispengya.hotkey.server.scheduler;

import com.ispengya.hotkey.remoting.message.HotKeyViewMessage;
import com.ispengya.hotkey.remoting.protocol.Command;
import com.ispengya.hotkey.remoting.protocol.CommandType;
import com.ispengya.hotkey.remoting.protocol.Serializer;
import com.ispengya.hotkey.remoting.server.ServerChannelManager;
import com.ispengya.hotkey.server.monitor.MonitorMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * HotKeyChangePublisher 负责将热 key 新增事件异步推送给客户端。
 *
 * <p>计算线程提交变更事件到队列，后台线程消费并推送，解耦计算与网络 IO。</p>
 */
public final class HotKeyChangePublisher {

    private static final Logger log = LoggerFactory.getLogger(HotKeyChangePublisher.class);
    private final ServerChannelManager channelManager;
    private final Serializer serializer;
    private final BlockingQueue<PushEvent> queue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService executor;
    private final boolean debugEnabled;

    public HotKeyChangePublisher(ServerChannelManager channelManager, Serializer serializer, boolean debugEnabled) {
        this.channelManager = channelManager;
        this.serializer = serializer;
        this.debugEnabled = debugEnabled;
        this.executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            private int index = 0;

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "hotkey-change-publisher-" + index++);
                t.setDaemon(true);
                return t;
            }
        });
        this.executor.scheduleAtFixedRate(this::flush, 10L, 10L, TimeUnit.MILLISECONDS);
    }

    public void publish(String appName, String key) {
        if (appName == null || key == null) {
            return;
        }
        queue.offer(new PushEvent(appName, key));
    }

    private void flush() {
        try {
            while (true) {
                PushEvent event = queue.take();
                if (debugEnabled && log.isDebugEnabled()) {
                    log.debug("Dequeue hot key change. appName={}, key={}",
                            event.appName, event.key);
                }
                sendSingle(event.appName, event.key);
            }
        } catch (InterruptedException e) {
            log.warn("HotKeyChangePublisher flush thread interrupted");
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            log.error("Unexpected error in HotKeyChangePublisher.flush", t);
        }
    }

    private void sendSingle(String appName, String key) {
        try {
            HotKeyViewMessage view = new HotKeyViewMessage();
            HotKeyViewMessage.ViewEntry entry = new HotKeyViewMessage.ViewEntry();
            entry.setAddedKey(key);
            Map<String, HotKeyViewMessage.ViewEntry> views = new HashMap<>();
            views.put(appName, entry);
            view.setViews(views);
            byte[] payload = serializer.serialize(view);
            Command command = new Command(CommandType.HOT_KEY_PUSH, 0L, payload);
            channelManager.broadcastToKey(appName, key, command);
            MonitorMetrics.PUSHED.increment();
        } catch (Exception e) {
            MonitorMetrics.PUSH_FAILED.increment();
            log.error("Failed to send hot key push. appName={}, key={}", appName, key, e);
        }
    }

    /**
     * 获取推送队列当前积压量。
     */
    public int getQueueSize() {
        return queue.size();
    }

    private static final class PushEvent {
        private final String appName;
        private final String key;

        private PushEvent(String appName, String key) {
            this.appName = appName;
            this.key = key;
        }
    }
}
