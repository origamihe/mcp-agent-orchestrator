package com.mcp.common.storage;

import java.time.Instant;

/**
 * 文件信息摘要 — 用于目录列表和文件查询。
 * 不包含文件内容，避免大文件的内存占用。
 */
public class FileInfo {
    private String path;
    private String name;
    private boolean directory;
    private long size;
    private Instant lastModified;
    private String language;
    private boolean readable;
    private boolean writable;

    public FileInfo() {}

    public FileInfo(String path, String name, boolean directory, long size, Instant lastModified) {
        this.path = path;
        this.name = name;
        this.directory = directory;
        this.size = size;
        this.lastModified = lastModified;
        this.readable = true;
        this.writable = true;
    }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isDirectory() { return directory; }
    public void setDirectory(boolean directory) { this.directory = directory; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public Instant getLastModified() { return lastModified; }
    public void setLastModified(Instant lastModified) { this.lastModified = lastModified; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public boolean isReadable() { return readable; }
    public void setReadable(boolean readable) { this.readable = readable; }

    public boolean isWritable() { return writable; }
    public void setWritable(boolean writable) { this.writable = writable; }

    @Override
    public String toString() {
        return (directory ? "[DIR]  " : "[FILE] ") + name +
                (size > 0 ? " (" + formatSize(size) + ")" : "") +
                (language != null ? " [" + language + "]" : "");
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}