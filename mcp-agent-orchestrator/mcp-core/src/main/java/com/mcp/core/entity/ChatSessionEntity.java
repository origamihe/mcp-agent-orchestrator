package com.mcp.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天会话实体
 */
@Entity
@Table(name = "chat_sessions", schema = "mcp_agent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionEntity {

    @Id
    @Column(length = 64)
    private String sessionId;

    @Column(length = 100)
    private String userId;

    @Column(length = 20)
    private String platform;

    @Column(length = 64)
    private String groupId;

    @Column(length = 100)
    private String agentId;

    @Column(length = 20, nullable = false)
    private String status = "active";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastActiveAt;

    @OneToMany(mappedBy = "chatSession", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<ChatMessageEntity> messages = new ArrayList<>();

    public void addMessage(ChatMessageEntity message) {
        messages.add(message);
        message.setChatSession(this);
        this.lastActiveAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastActiveAt = createdAt;
    }
}