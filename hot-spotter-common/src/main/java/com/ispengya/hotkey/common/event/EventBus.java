package com.ispengya.hotkey.common.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 简单的事件总线实现。
 *
 * <p>基于 Guava EventBus 思想，但不引入外部依赖。
 * 支持同步/异步事件分发，按事件类型分发给对应的监听器。</p>
 *
 * @author ispengya
 */
public final class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    /**
     * 事件类型 -> 监听器列表
     */
    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    /**
     * 注册监听器。
     *
     * @param eventType 事件类型
     * @param listener  监听器
     * @param <T>       事件类型
     */
    public <T> void register(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(listener);
        log.debug("Registered listener for event: {}", eventType.getSimpleName());
    }

    /**
     * 发布事件（同步）。
     *
     * @param event 事件对象
     * @param <T>   事件类型
     */
    @SuppressWarnings("unchecked")
    public <T> void post(T event) {
        if (event == null) {
            return;
        }

        Class<?> eventType = event.getClass();
        List<Consumer<?>> eventListeners = listeners.get(eventType);

        if (eventListeners == null || eventListeners.isEmpty()) {
            log.debug("No listeners for event: {}", eventType.getSimpleName());
            return;
        }

        for (Consumer<?> listener : eventListeners) {
            try {
                ((Consumer<T>) listener).accept(event);
            } catch (Exception e) {
                log.error("Error handling event: {}", eventType.getSimpleName(), e);
            }
        }
    }

    /**
     * 清除所有监听器。
     */
    public void clear() {
        listeners.clear();
    }
}
