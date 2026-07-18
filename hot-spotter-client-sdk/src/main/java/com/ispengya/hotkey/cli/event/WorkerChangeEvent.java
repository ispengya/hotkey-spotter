package com.ispengya.hotkey.cli.event;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Worker 节点变更事件。
 *
 * @author ispengya
 */
public final class WorkerChangeEvent {

    private final List<InetSocketAddress> workers;

    public WorkerChangeEvent(List<InetSocketAddress> workers) {
        this.workers = workers;
    }

    public List<InetSocketAddress> getWorkers() {
        return workers;
    }
}
