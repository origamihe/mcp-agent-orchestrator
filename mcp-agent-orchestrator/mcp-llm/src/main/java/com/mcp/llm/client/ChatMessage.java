package com.mcp.llm.client;

import com.mcp.core.domain.chat.CoreChatMessage;  // 避免名称冲突
import com.mcp.core.domain.chat.MessageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    private String role;      // "system", "user", "assistant", "tool"
    private String content;

    /** tool 角色：关联的 tool_call id */
    private String toolCallId;
    /** tool 角色：工具名称 */
    private String name;
    /** assistant 角色：LLM 请求的工具调用列表 */
    private List<Map<String, Object>> toolCalls;
    /** 消息类型标记 - 用于 Memory 过滤和对话结构化 */
    @Builder.Default
    private MessageType messageType = MessageType.NORMAL;

    // ========== 与 Core Domain 互转 ==========

    public CoreChatMessage toCore(String sessionId) {
        return CoreChatMessage.builder()
                .sessionId(sessionId)
                .role(MessageRole.valueOf(role.toUpperCase()))
                .content(content)
                .build();
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