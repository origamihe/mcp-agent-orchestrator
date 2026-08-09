package com.mcp.common.storage;

/**
 * 存储配额配置 — 控制 Agent Runtime 的存储上限。
 * 当超过配额时，StorageManager 会触发清理或拒绝操作。
 */
public class StorageQuota {
    private long maxDiskUsageMB = 500;
    private long maxArtifactsPerSession = 1000;
    private long maxFilesPerWorkspace = 200;
    private long maxFileSizeBytes = 50 * 1024 * 1024; // 50MB
    private int retentionDays = 30;
    private boolean autoCleanup = true;
    private double cleanupThreshold = 0.8; // 80% 时触发清理

    public StorageQuota() {}

    public long getMaxDiskUsageMB() { return maxDiskUsageMB; }
    public void setMaxDiskUsageMB(long maxDiskUsageMB) { this.maxDiskUsageMB = maxDiskUsageMB; }

    public long getMaxDiskUsageBytes() { return maxDiskUsageMB * 1024 * 1024; }

    public long getMaxArtifactsPerSession() { return maxArtifactsPerSession; }
    public void setMaxArtifactsPerSession(long maxArtifactsPerSession) { this.maxArtifactsPerSession = maxArtifactsPerSession; }

    public long getMaxFilesPerWorkspace() { return maxFilesPerWorkspace; }
    public void setMaxFilesPerWorkspace(long maxFilesPerWorkspace) { this.maxFilesPerWorkspace = maxFilesPerWorkspace; }

    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }

    public int getRetentionDays() { return retentionDays; }
    public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }

    public boolean isAutoCleanup() { return autoCleanup; }
    public void setAutoCleanup(boolean autoCleanup) { this.autoCleanup = autoCleanup; }

    public double getCleanupThreshold() { return cleanupThreshold; }
    public void setCleanupThreshold(double cleanupThreshold) { this.cleanupThreshold = cleanupThreshold; }
}