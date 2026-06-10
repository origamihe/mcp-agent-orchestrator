package com.mcp.common.channel;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class ChannelReply {
    private String channelType;
    private String targetId;          // 回复目标（群ID或用户ID）
    private String content;           // 纯文本回复内容
    private ChannelMessage.ChatType chatType;
    private Map<String, Object> metadata; // 扩展元数据
}