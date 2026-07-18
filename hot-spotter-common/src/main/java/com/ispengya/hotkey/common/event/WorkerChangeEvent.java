package com.ispengya.hotkey.common.event;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Worker 地址变更事件。
 *
 * @author ispengya
 */
public class WorkerChangeEvent {

    private final List<InetSocketAddress> workers;

    public WorkerChangeEvent(List<InetSocketAddress> workers) {
        this.workers = workers;
    }

    public List<InetSocketAddress> getWorkers() {
        return workers;
    }
}
