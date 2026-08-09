package com.mcp.common.delivery;

import java.time.Instant;

/**
 * 投递结果 — 记录单次投递的详细结果。
 */
public class DeliveryResult {
    private String messageId;
    private String channelType;
    private String targetId;
    private DeliveryStatus status;
    private Instant sentAt;
    private Instant completedAt;
    private int attemptCount;
    private int maxRetries;
    private String errorMessage;
    private String errorCode;
    private long responseTimeMs;

    public DeliveryResult() {}

    public static DeliveryResult success(String messageId, String channelType, String targetId) {
        DeliveryResult result = new DeliveryResult();
        result.messageId = messageId;
        result.channelType = channelType;
        result.targetId = targetId;
        result.status = DeliveryStatus.DELIVERED;
        result.completedAt = Instant.now();
        return result;
    }

    public static DeliveryResult failure(String messageId, String channelType, String targetId, String errorMessage) {
        DeliveryResult result = new DeliveryResult();
        result.messageId = messageId;
        result.channelType = channelType;
        result.targetId = targetId;
        result.status = DeliveryStatus.FAILED;
        result.errorMessage = errorMessage;
        result.completedAt = Instant.now();
        return result;
    }

    public boolean isSuccess() {
        return status == DeliveryStatus.DELIVERED;
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public long getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(long responseTimeMs) { this.responseTimeMs = responseTimeMs; }

    @Override
    public String toString() {
        return "DeliveryResult{" +
                "messageId='" + messageId + '\'' +
                ", channel='" + channelType + '\'' +
                ", target='" + targetId + '\'' +
                ", status=" + status +
                (errorMessage != null ? ", error='" + errorMessage + '\'' : "") +
                ", attempts=" + attemptCount + "/" + maxRetries +
                '}';
    }
}