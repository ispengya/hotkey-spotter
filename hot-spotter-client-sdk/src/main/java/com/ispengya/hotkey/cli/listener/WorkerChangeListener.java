package com.ispengya.hotkey.cli.listener;

import com.ispengya.hotkey.common.event.EventBus;
import com.ispengya.hotkey.common.event.WorkerChangeEvent;
import com.ispengya.hotkey.remoting.client.NettyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Worker 变更监听器，从 EventBus 订阅 WorkerChangeEvent，
 * 并将变更通知给 NettyClient 更新连接。
 *
 * @author ispengya
 */
public class WorkerChangeListener {

    private static final Logger log = LoggerFactory.getLogger(WorkerChangeListener.class);

    public WorkerChangeListener(EventBus eventBus, NettyClient nettyClient) {
        eventBus.register(WorkerChangeEvent.class, event -> {
            log.info("Worker list changed, size={}", event.getWorkers().size());
            nettyClient.onWorkerChange(event.getWorkers());
        });
    }
}
