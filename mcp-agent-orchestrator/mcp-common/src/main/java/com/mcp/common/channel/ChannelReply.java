package com.mcp.common.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    // === IDE Host 回复 ===
    private List<FileEdit> fileEdits = new ArrayList<>();   // 代码修改（Diff apply）
    private String terminalCommand;                          // 要执行的终端命令

    // === Desktop Host 回复 ===
    private String notificationTitle;                        // 系统通知标题
    private String notificationBody;                         // 系统通知内容

    // === 通用 Host 动作 ===
    private String hostAction;                               // 动作类型：APPLY_DIFF, EXEC_COMMAND, SHOW_NOTIFICATION, OPEN_FILE

    public ChannelReply() {}

    private ChannelReply(Builder builder) {
        this.channelType = builder.channelType;
        this.targetId = builder.targetId;
        this.content = builder.content;
        this.chatType = builder.chatType;
        this.metadata = builder.metadata;
        this.voiceUrl = builder.voiceUrl;
        this.voiceData = builder.voiceData;
        this.sendAsVoice = builder.sendAsVoice;
        this.filePath = builder.filePath;
        this.fileUrl = builder.fileUrl;
        this.sendAsFile = builder.sendAsFile;
        this.fileEdits = builder.fileEdits != null ? builder.fileEdits : new ArrayList<>();
        this.terminalCommand = builder.terminalCommand;
        this.notificationTitle = builder.notificationTitle;
        this.notificationBody = builder.notificationBody;
        this.hostAction = builder.hostAction;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 文件编辑操作 — 用于 IDE Host 的 Diff apply。
     */
    public static class FileEdit {
        private String filePath;
        private String originalContent;
        private String newContent;
        private String diff;
        private int startLine;
        private int endLine;

        public FileEdit() {}

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getOriginalContent() { return originalContent; }
        public void setOriginalContent(String originalContent) { this.originalContent = originalContent; }
        public String getNewContent() { return newContent; }
        public void setNewContent(String newContent) { this.newContent = newContent; }
        public String getDiff() { return diff; }
        public void setDiff(String diff) { this.diff = diff; }
        public int getStartLine() { return startLine; }
        public void setStartLine(int startLine) { this.startLine = startLine; }
        public int getEndLine() { return endLine; }
        public void setEndLine(int endLine) { this.endLine = endLine; }
    }

    public static class Builder {
        private String channelType;
        private String targetId;
        private String content;
        private ChannelMessage.ChatType chatType;
        private Map<String, Object> metadata;
        private String voiceUrl;
        private byte[] voiceData;
        private boolean sendAsVoice;
        private String filePath;
        private String fileUrl;
        private boolean sendAsFile;
        private List<FileEdit> fileEdits;
        private String terminalCommand;
        private String notificationTitle;
        private String notificationBody;
        private String hostAction;

        public Builder channelType(String channelType) { this.channelType = channelType; return this; }
        public Builder targetId(String targetId) { this.targetId = targetId; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder chatType(ChannelMessage.ChatType chatType) { this.chatType = chatType; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public Builder voiceUrl(String voiceUrl) { this.voiceUrl = voiceUrl; return this; }
        public Builder voiceData(byte[] voiceData) { this.voiceData = voiceData; return this; }
        public Builder sendAsVoice(boolean sendAsVoice) { this.sendAsVoice = sendAsVoice; return this; }
        public Builder filePath(String filePath) { this.filePath = filePath; return this; }
        public Builder fileUrl(String fileUrl) { this.fileUrl = fileUrl; return this; }
        public Builder sendAsFile(boolean sendAsFile) { this.sendAsFile = sendAsFile; return this; }
        public Builder fileEdits(List<FileEdit> fileEdits) { this.fileEdits = fileEdits; return this; }
        public Builder terminalCommand(String terminalCommand) { this.terminalCommand = terminalCommand; return this; }
        public Builder notificationTitle(String notificationTitle) { this.notificationTitle = notificationTitle; return this; }
        public Builder notificationBody(String notificationBody) { this.notificationBody = notificationBody; return this; }
        public Builder hostAction(String hostAction) { this.hostAction = hostAction; return this; }
        public ChannelReply build() { return new ChannelReply(this); }
    }

    // === Getters ===

    public String getChannelType() { return channelType; }
    public String getTargetId() { return targetId; }
    public String getContent() { return content; }
    public ChannelMessage.ChatType getChatType() { return chatType; }
    public Map<String, Object> getMetadata() { return metadata; }
    public String getVoiceUrl() { return voiceUrl; }
    public byte[] getVoiceData() { return voiceData; }
    public boolean isSendAsVoice() { return sendAsVoice; }
    public String getFilePath() { return filePath; }
    public String getFileUrl() { return fileUrl; }
    public boolean isSendAsFile() { return sendAsFile; }
    public List<FileEdit> getFileEdits() { return fileEdits; }
    public String getTerminalCommand() { return terminalCommand; }
    public String getNotificationTitle() { return notificationTitle; }
    public String getNotificationBody() { return notificationBody; }
    public String getHostAction() { return hostAction; }
}