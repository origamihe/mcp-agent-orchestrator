package com.mcp.engine.trace;

/**
 * 当前请求的 SessionTrace 持有者 — 使用 ThreadLocal 在请求生命周期内传递 SessionTrace。
 *
 * 使用方式：
 * <pre>
 * SessionTraceHolder.start("session-123", store);
 * try {
 *     SessionTraceHolder.current().recordUserMessage("hello", 5);
 *     // ... pipeline execution ...
 * } finally {
 *     SessionTrace trace = SessionTraceHolder.end();
 *     ContractVerifier.createDefault().verify(trace.getEvents());
 * }
 * </pre>
 */
public final class SessionTraceHolder {

    private static final ThreadLocal<SessionTrace> CURRENT = new ThreadLocal<>();

    private SessionTraceHolder() {}

    public static void start(String sessionId, SessionEventStore store) {
        CURRENT.set(new SessionTrace(sessionId, store));
    }

    public static SessionTrace current() {
        SessionTrace trace = CURRENT.get();
        if (trace == null) {
            throw new IllegalStateException("No SessionTrace in current thread. Call SessionTraceHolder.start() first.");
        }
        return trace;
    }

    public static SessionTrace currentOrNull() {
        return CURRENT.get();
    }

    public static SessionTrace end() {
        SessionTrace trace = CURRENT.get();
        CURRENT.remove();
        return trace;
    }

    public static boolean isActive() {
        return CURRENT.get() != null;
    }
}