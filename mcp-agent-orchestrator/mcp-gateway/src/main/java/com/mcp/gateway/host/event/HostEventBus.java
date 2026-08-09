package com.mcp.gateway.host.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Host 事件总线 — 接收插件发来的 IDE 事件，分发给所有注册的监听器。
 * Memory、Planner、Skill、Workspace 等模块各自订阅感兴趣的事件。
 */
@Slf4j
@Component
public class HostEventBus {

    private final List<Consumer<HostEvent>> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<HostEvent> listener) {
        listeners.add(listener);
    }

    public void unsubscribe(Consumer<HostEvent> listener) {
        listeners.remove(listener);
    }

    public void publish(HostEvent event) {
        log.debug("[HostEventBus] Event: {} | workspace={} | payload={}",
                event.getType(), event.getWorkspaceId(), event.getPayload());
        for (Consumer<HostEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("[HostEventBus] Listener error for event {}: {}",
                        event.getType(), e.getMessage());
            }
        }
    }
}