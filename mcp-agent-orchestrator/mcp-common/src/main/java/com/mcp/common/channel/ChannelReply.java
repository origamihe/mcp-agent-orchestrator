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
    private String voiceUrl;          // 语音文件 URL（TTS 生成后）
    private byte[] voiceData;         // 语音文件二进制数据（OneBot base64 等）
    private boolean sendAsVoice;      // 是否以语音方式发送
    private String filePath;          // 本地文件路径（用于上传到OneBot）
    private String fileUrl;           // 文件下载URL（用于前端监控面板）
    private boolean sendAsFile;       // 是否以文件方式发送
}