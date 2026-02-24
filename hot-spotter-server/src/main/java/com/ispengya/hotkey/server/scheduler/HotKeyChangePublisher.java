package com.ispengya.hotkey.server.scheduler;

import com.ispengya.hotkey.remoting.message.HotKeyViewMessage;
import com.ispengya.hotkey.remoting.protocol.Command;
import com.ispengya.hotkey.remoting.protocol.CommandType;
import com.ispengya.hotkey.remoting.protocol.Serializer;
import com.ispengya.hotkey.remoting.server.ServerChannelManager;
import com.ispengya.hotkey.server.model.HotKeyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
* HotKeyChangePublisher 负责将服务端计算出的热 key 变更异步推送给所有客户端。
*
* <p>调用方（例如 HotKeyComputeTask、HotKeyDecayTask）只需提交本次计算的整体结果
* 以及新增 / 删除的 key 集合，本类会将每个 key 的变更包装成单条事件放入队列，由
* 后台单线程循环从阻塞队列中取出并通过 HOT_KEY_PUSH 命令广播给所有推送连接。</p>
*
* <p>这种设计将「热 key 计算 / 衰减」与「网络推送」解耦，避免在计算线程中执行 IO，
* 同时保证同一进程内的推送顺序与队列顺序一致。</p>
 */
public final class HotKeyChangePublisher {

    private static final Logger log = LoggerFactory.getLogger(HotKeyChangePublisher.class);
    private final ServerChannelManager channelManager;
    private final Serializer serializer;
    private final BlockingQueue<PushEvent> queue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService executor;

    public HotKeyChangePublisher(ServerChannelManager channelManager, Serializer serializer) {
        this.channelManager = channelManager;
        this.serializer = serializer;
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

    public void publish(HotKeyResult result, Set<String> addedKeys, Set<String> removedKeys) {
        if (result == null) {
            return;
        }
        if ((addedKeys == null || addedKeys.isEmpty()) && (removedKeys == null || removedKeys.isEmpty())) {
            return;
        }
        if (addedKeys != null) {
            for (String key : addedKeys) {
                if (key == null) {
                    continue;
                }
                enqueue(result, key, true);
            }
        }
        if (removedKeys != null) {
            for (String key : removedKeys) {
                if (key == null) {
                    continue;
                }
                enqueue(result, key, false);
            }
        }
        if (log.isDebugEnabled()) {
            int addCount = addedKeys == null ? 0 : addedKeys.size();
            int removeCount = removedKeys == null ? 0 : removedKeys.size();
            log.debug("Enqueue hot key changes. appName={}, addCount={}, removeCount={}",
                    result.getAppName(), addCount, removeCount);
        }
    }

    private void enqueue(HotKeyResult result, String key, boolean add) {
        queue.offer(new PushEvent(result, key, add));
    }

    private void flush() {
        try {
            while (true) {
                PushEvent event = queue.take();
                if (log.isDebugEnabled()) {
                    log.debug("Dequeue hot key change. appName={}, key={}, add={}",
                            event.result.getAppName(), event.key, event.add);
                }
                sendSingle(event.result, event.key, event.add);
            }
        } catch (InterruptedException e) {
            if (log.isWarnEnabled()) {
                log.warn("HotKeyChangePublisher flush thread interrupted", e);
            }
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            if (log.isErrorEnabled()) {
                log.error("Unexpected error in HotKeyChangePublisher.flush", t);
            }
        }
    }

    private void sendSingle(HotKeyResult result, String key, boolean add) {
        HotKeyViewMessage view = new HotKeyViewMessage();
        HotKeyViewMessage.ViewEntry entry = new HotKeyViewMessage.ViewEntry();
        if (add) {
            entry.setAddedKey(key);
        } else {
            entry.setRemovedKey(key);
        }
        Map<String, HotKeyViewMessage.ViewEntry> views = new HashMap<>();
        views.put(result.getAppName(), entry);
        view.setViews(views);
        byte[] payload = serializer.serialize(view);
        Command command = new Command(CommandType.HOT_KEY_PUSH, 0L, payload);
        String appName = result.getAppName();
        if (appName == null || appName.isEmpty()) {
            channelManager.broadcastOnPushChannels(command);
        } else {
            channelManager.broadcastToApp(appName, command);
        }
    }

    private static final class PushEvent {

        private final HotKeyResult result;
        private final String key;
        private final boolean add;

        private PushEvent(HotKeyResult result, String key, boolean add) {
            this.result = result;
            this.key = key;
            this.add = add;
        }
    }
}
