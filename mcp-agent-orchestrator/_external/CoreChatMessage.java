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
    private String senderId;
    private String senderName;
    private MessageRole role;
    private String content;
    private String toolCalls;

    // 关键修复：使用 @Builder.Default
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 格式化为群聊历史记录（标注说话人）
     */
    public String toHistoryLine() {
        String label = senderName != null ? senderName : senderId;
        if (role == MessageRole.ASSISTANT) {
            label = "澪音";
        }
        return "[" + label + "]\n" + content;
    }
}