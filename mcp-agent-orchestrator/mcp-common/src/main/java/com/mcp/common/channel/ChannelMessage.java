package com.mcp.common.channel;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ChannelMessage {
    private String channelType;       // "qq", "telegram", "discord", "ide", "desktop"
    private String messageId;         // 平台消息ID
    private String senderId;          // 发送者ID
    private String senderName;        // 发送者昵称
    private String content;           // 纯文本内容（已去除平台特有格式）
    private String chatId;            // 会话ID（群ID或私聊ID）
    private ChatType chatType;        // PRIVATE / GROUP / CHANNEL / HOST
    private Map<String, Object> raw;  // 原始消息（平台特有数据）
    private String platformSessionId; // 平台会话ID（用于上下文管理）

    private List<String> mentionedUsers;   // 被 @ 的用户ID列表（QQ号等）
    private boolean mentionedAgent;         // 是否 @了 Agent/Bot
    private String replyToMessageId;        // 回复的消息ID（引用回复）

    /**
     * Host 上下文 — Host 向 Agent 提供的世界感知能力。
     * 不同 Host 填充不同的字段子集：
     * - IDE Host: currentFilePath, gitDiff, diagnostics...
     * - Desktop Host: clipboardContent, activeWindowTitle...
     * - QQ Host: userId, chatId, groupMemberIds...
     */
    private HostContext hostContext;

    public enum ChatType {
        PRIVATE, GROUP, CHANNEL, HOST
    }
}