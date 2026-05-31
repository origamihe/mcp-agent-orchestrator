package com.mcp.core.domain.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天消息 - 领域模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoreChatMessage {

    private String messageId;
    private String sessionId;
    private MessageRole role;
    private String content;
    private String toolCalls;

    // 关键修复：使用 @Builder.Default
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}