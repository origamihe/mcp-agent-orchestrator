package com.mcp.engine.trace;

import java.util.List;

/**
 * 会话事件存储 — append-only 事件日志。
 *
 * 约束：
 * - 只允许追加（append），不允许修改或删除
 * - 每个 session 的事件按 sequence 严格递增
 * - 支持按 sessionId 查询完整事件流
 *
 * 默认实现为 NOOP（不存储），生产环境可注入持久化实现。
 * 测试环境使用 InMemory 实现进行验证。
 */
public interface SessionEventStore {

    void append(SessionEvent event);

    List<SessionEvent> getBySessionId(String sessionId);

    List<SessionEvent> getByTraceId(String traceId);

    int size(String sessionId);

    SessionEventStore NOOP = new SessionEventStore() {
        @Override
        public void append(SessionEvent event) {}

        @Override
        public List<SessionEvent> getBySessionId(String sessionId) { return List.of(); }

        @Override
        public List<SessionEvent> getByTraceId(String traceId) { return List.of(); }

        @Override
        public int size(String sessionId) { return 0; }
    };

    class InMemory implements SessionEventStore {
        private final java.util.concurrent.ConcurrentHashMap<String, java.util.List<SessionEvent>> store = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void append(SessionEvent event) {
            store.computeIfAbsent(event.sessionId(), k -> java.util.Collections.synchronizedList(new java.util.ArrayList<>()))
                    .add(event);
        }

        @Override
        public List<SessionEvent> getBySessionId(String sessionId) {
            return store.getOrDefault(sessionId, List.of());
        }

        @Override
        public List<SessionEvent> getByTraceId(String traceId) {
            return store.values().stream()
                    .flatMap(List::stream)
                    .filter(e -> e.traceId().equals(traceId))
                    .toList();
        }

        @Override
        public int size(String sessionId) {
            return store.getOrDefault(sessionId, List.of()).size();
        }

        public void clear() {
            store.clear();
        }
    }
}