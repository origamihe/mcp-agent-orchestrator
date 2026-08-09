package com.mcp.common.delivery;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 投递消息 — 统一的消息投递模型。
 * 与 ChannelMessage（入站）不同，DeliveryMessage 是出站投递的抽象，
 * 支持文本、文件、图片、Markdown 等多种内容类型。
 */
public class DeliveryMessage {
    private String id;
    private String channelType;
    private String targetId;
    private String content;
    private ContentType contentType;
    private String filePath;
    private String fileUrl;
    private String fileName;
    private Map<String, Object> metadata;
    private int priority;
    private Instant createdAt;
    private Instant scheduledAt;

    public enum ContentType {
        TEXT,
        MARKDOWN,
        HTML,
        FILE,
        IMAGE,
        VOICE,
        NOTIFICATION
    }

    public DeliveryMessage() {
        this.id = UUID.randomUUID().toString();
        this.contentType = ContentType.TEXT;
        this.priority = 5;
        this.createdAt = Instant.now();
        this.metadata = new LinkedHashMap<>();
    }

    public static DeliveryMessage text(String channelType, String targetId, String content) {
        DeliveryMessage msg = new DeliveryMessage();
        msg.channelType = channelType;
        msg.targetId = targetId;
        msg.content = content;
        msg.contentType = ContentType.TEXT;
        return msg;
    }

    public static DeliveryMessage file(String channelType, String targetId, String filePath, String caption) {
        DeliveryMessage msg = new DeliveryMessage();
        msg.channelType = channelType;
        msg.targetId = targetId;
        msg.filePath = filePath;
        msg.content = caption;
        msg.contentType = ContentType.FILE;
        return msg;
    }

    public static DeliveryMessage markdown(String channelType, String targetId, String content) {
        DeliveryMessage msg = new DeliveryMessage();
        msg.channelType = channelType;
        msg.targetId = targetId;
        msg.content = content;
        msg.contentType = ContentType.MARKDOWN;
        return msg;
    }

    public static DeliveryMessage notification(String channelType, String targetId, String title, String body) {
        DeliveryMessage msg = new DeliveryMessage();
        msg.channelType = channelType;
        msg.targetId = targetId;
        msg.content = title;
        msg.addMetadata("body", body);
        msg.contentType = ContentType.NOTIFICATION;
        return msg;
    }

    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public ContentType getContentType() { return contentType; }
    public void setContentType(ContentType contentType) { this.contentType = contentType; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
}