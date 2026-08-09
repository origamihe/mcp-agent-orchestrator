package com.mcp.common.storage;

import java.time.Instant;

/**
 * 存储统计信息 — 跟踪 Agent Runtime 的存储使用情况。
 */
public class StorageStats {
    private long totalArtifacts;
    private long totalWorkspaceFiles;
    private long totalDiskUsageBytes;
    private long totalDbSizeBytes;
    private long largestFileBytes;
    private String largestFilePath;
    private Instant lastCleanup;
    private Instant collectedAt;

    public StorageStats() {
        this.collectedAt = Instant.now();
    }

    public long getTotalArtifacts() { return totalArtifacts; }
    public void setTotalArtifacts(long totalArtifacts) { this.totalArtifacts = totalArtifacts; }

    public long getTotalWorkspaceFiles() { return totalWorkspaceFiles; }
    public void setTotalWorkspaceFiles(long totalWorkspaceFiles) { this.totalWorkspaceFiles = totalWorkspaceFiles; }

    public long getTotalDiskUsageBytes() { return totalDiskUsageBytes; }
    public void setTotalDiskUsageBytes(long totalDiskUsageBytes) { this.totalDiskUsageBytes = totalDiskUsageBytes; }

    public long getTotalDbSizeBytes() { return totalDbSizeBytes; }
    public void setTotalDbSizeBytes(long totalDbSizeBytes) { this.totalDbSizeBytes = totalDbSizeBytes; }

    public long getLargestFileBytes() { return largestFileBytes; }
    public void setLargestFileBytes(long largestFileBytes) { this.largestFileBytes = largestFileBytes; }

    public String getLargestFilePath() { return largestFilePath; }
    public void setLargestFilePath(String largestFilePath) { this.largestFilePath = largestFilePath; }

    public Instant getLastCleanup() { return lastCleanup; }
    public void setLastCleanup(Instant lastCleanup) { this.lastCleanup = lastCleanup; }

    public Instant getCollectedAt() { return collectedAt; }
    public void setCollectedAt(Instant collectedAt) { this.collectedAt = collectedAt; }

    public long getTotalUsageBytes() {
        return totalDiskUsageBytes + totalDbSizeBytes;
    }

    public String formatTotalUsage() {
        long total = getTotalUsageBytes();
        if (total < 1024) return total + " B";
        if (total < 1024 * 1024) return String.format("%.1f KB", total / 1024.0);
        if (total < 1024 * 1024 * 1024) return String.format("%.1f MB", total / (1024.0 * 1024));
        return String.format("%.2f GB", total / (1024.0 * 1024 * 1024));
    }

    @Override
    public String toString() {
        return "StorageStats{" +
                "artifacts=" + totalArtifacts +
                ", workspaceFiles=" + totalWorkspaceFiles +
                ", totalUsage=" + formatTotalUsage() +
                ", largestFile=" + (largestFilePath != null ? largestFilePath : "none") +
                ", lastCleanup=" + lastCleanup +
                '}';
    }
}