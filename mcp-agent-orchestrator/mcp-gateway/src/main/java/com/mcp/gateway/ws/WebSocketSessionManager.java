package com.mcp.gateway.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    private final Map<String, String> aliases = new ConcurrentHashMap<>();

    public void register(String sessionId, WebSocketSession session) {
        sessions.put(sessionId, session);
    }

    public void registerAlias(String aliasSessionId, String originalSessionId) {
        WebSocketSession session = sessions.get(originalSessionId);
        if (session != null) {
            sessions.put(aliasSessionId, session);
            aliases.put(aliasSessionId, originalSessionId);
        }
    }

    public void unregister(String sessionId) {
        sessions.remove(sessionId);
        aliases.entrySet().removeIf(e -> e.getValue().equals(sessionId));
        aliases.remove(sessionId);
    }

    public void broadcast(String message) {
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                session.send(Mono.just(session.textMessage(message)))
                        .subscribe(
                                v -> {},
                                e -> System.err.println("[WS Broadcast] Failed: " + e.getMessage())
                        );
            }
        }
    }

    public void sendTo(String sessionId, String message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            session.send(Mono.just(session.textMessage(message)))
                    .subscribe(
                            v -> {},
                            e -> System.err.println("[WS SendTo] Failed for session " + sessionId + ": " + e.getMessage())
                    );
        }
    }

    public boolean hasSession(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        return session != null && session.isOpen();
    }
}