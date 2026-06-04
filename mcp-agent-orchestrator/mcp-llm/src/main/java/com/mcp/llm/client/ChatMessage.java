package com.mcp.llm.client;

import com.mcp.core.domain.chat.CoreChatMessage;  // 避免名称冲突
import com.mcp.core.domain.chat.MessageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    private String role;      // "system", "user", "assistant", "tool"
    private String content;

    // ========== 与 Core Domain 互转 ==========

    public CoreChatMessage toCore(String sessionId) {
        return new CoreChatMessage(
                null,                           // messageId 可由数据库生成
                sessionId,
                MessageRole.valueOf(role.toUpperCase()),
                content,
                null,                           // toolCalls
                null                            // createdAt
        );
    }

    public static ChatMessage fromCore(CoreChatMessage core) {
        return ChatMessage.builder()
                .role(core.getRole().getCode())
                .content(core.getContent())
                .build();
    }

    public static List<ChatMessage> fromCoreList(List<CoreChatMessage> coreList) {
        return coreList.stream().map(ChatMessage::fromCore).toList();
    }
}