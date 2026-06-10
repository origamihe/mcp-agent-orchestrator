package com.mcp.common.channel;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class ChannelMessage {
    private String channelType;       // "qq", "telegram", "discord"
    private String messageId;         // 平台消息ID
    private String senderId;          // 发送者ID
    private String senderName;        // 发送者昵称
    private String content;           // 纯文本内容（已去除平台特有格式）
    private String chatId;            // 会话ID（群ID或私聊ID）
    private ChatType chatType;        // PRIVATE / GROUP / CHANNEL
    private Map<String, Object> raw;  // 原始消息（平台特有数据）
    private String platformSessionId; // 平台会话ID（用于上下文管理）

    public enum ChatType {
        PRIVATE, GROUP, CHANNEL
    }
}