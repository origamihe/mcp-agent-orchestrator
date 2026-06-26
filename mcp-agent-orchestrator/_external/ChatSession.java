package com.mcp.core.domain.chat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聊天会话 - 领域模型
 */
public class ChatSession {

    private final String sessionId;
    private final String userId;
    private final LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    private final List<CoreChatMessage> messages;

    public ChatSession(String sessionId, String userId, LocalDateTime createdAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.createdAt = createdAt;
        this.lastActiveAt = createdAt;
        this.messages = new ArrayList<>();
    }

    public void addMessage(CoreChatMessage message) {
        this.messages.add(message);
        this.lastActiveAt = LocalDateTime.now();
    }

    public List<CoreChatMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    // Getters
    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
}