package com.mcp.engine.storage;

import com.mcp.common.storage.FileInfo;
import com.mcp.common.storage.StorageQuota;
import com.mcp.common.storage.StorageStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2 验证 — StorageManager 测试。
 * 验证：
 * 1. 文件读写（readFile / writeFile）
 * 2. 目录列表（listDirectory）
 * 3. 文件删除（deleteFile）
 * 4. 文件存在检查（fileExists）
 * 5. 追加写入（appendToFile）
 * 6. 存储统计（collectStats）
 * 7. 存储配额（StorageQuota）
 * 8. FileInfo 模型
 * 9. StorageStats 模型
 */
@DisplayName("StorageManager — P2 存储管理器测试")
class StorageManagerTest {

    @TempDir
    Path tempDir;

    private StorageManager storageManager;

    @BeforeEach
    void setUp() {
        storageManager = new StorageManager(null, null);
    }

    // ==================== 文件读写 ====================

    @Nested
    @DisplayName("writeFile / readFile — 文件写入与读取")
    class WriteReadFile {

        @Test
        @DisplayName("写入文件后应能读取相同内容")
        void shouldWriteAndReadFile() throws IOException {
            Path file = tempDir.resolve("test.txt");
            String content = "Hello, Agent Runtime!";

            boolean written = storageManager.writeFile(file, content);
            assertThat(written).isTrue();

            Optional<String> read = storageManager.readFile(file);
            assertThat(read).isPresent();
            assertThat(read.get()).isEqualTo(content);
        }

        @Test
        @DisplayName("写入时应自动创建父目录")
        void shouldCreateParentDirectories() throws IOException {
            Path file = tempDir.resolve("deep/nested/dir/test.txt");
            String content = "deep content";

            boolean written = storageManager.writeFile(file, content);
            assertThat(written).isTrue();
            assertThat(Files.exists(file)).isTrue();

            Optional<String> read = storageManager.readFile(file);
            assertThat(read).isPresent();
            assertThat(read.get()).isEqualTo(content);
        }

        @Test
        @DisplayName("读取不存在的文件应返回 empty")
        void shouldReturnEmptyForNonExistentFile() {
            Path file = tempDir.resolve("nonexistent.txt");
            Optional<String> result = storageManager.readFile(file);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("写入 null 内容应返回 false")
        void shouldReturnFalseForNullContent() {
            Path file = tempDir.resolve("null.txt");
            boolean written = storageManager.writeFile(file, null);
            assertThat(written).isFalse();
        }

        @Test
        @DisplayName("写入应覆盖已有文件")
        void shouldOverwriteExistingFile() throws IOException {
            Path file = tempDir.resolve("overwrite.txt");
            storageManager.writeFile(file, "original");
            storageManager.writeFile(file, "updated");

            Optional<String> read = storageManager.readFile(file);
            assertThat(read).isPresent();
            assertThat(read.get()).isEqualTo("updated");
        }

        @Test
        @DisplayName("写入多行内容应正确保留换行符")
        void shouldPreserveNewlines() {
            Path file = tempDir.resolve("multiline.txt");
            String content = "line1\nline2\nline3\n";

            storageManager.writeFile(file, content);
            Optional<String> read = storageManager.readFile(file);
            assertThat(read).isPresent();
            assertThat(read.get()).isEqualTo(content);
        }

        @Test
        @DisplayName("写入中文内容应正确保留")
        void shouldPreserveChineseCharacters() {
            Path file = tempDir.resolve("chinese.txt");
            String content = "你好，Agent 运行时！这是一个测试文件。";

            storageManager.writeFile(file, content);
            Optional<String> read = storageManager.readFile(file);
            assertThat(read).isPresent();
            assertThat(read.get()).isEqualTo(content);
        }
    }

    // ==================== 追加写入 ====================

    @Nested
    @DisplayName("appendToFile — 追加写入")
    class AppendToFile {

        @Test
        @DisplayName("追加写入应保留原有内容")
        void shouldAppendToExistingFile() {
            Path file = tempDir.resolve("append.txt");
            storageManager.writeFile(file, "first line\n");
            storageManager.appendToFile(file, "second line\n");

            Optional<String> read = storageManager.readFile(file);
            assertThat(read).isPresent();
            assertThat(read.get()).isEqualTo("first line\nsecond line\n");
        }

        @Test
        @DisplayName("追加到不存在的文件应自动创建")
        void shouldCreateFileOnAppend() {
            Path file = tempDir.resolve("new_append.txt");
            storageManager.appendToFile(file, "new content");

            Optional<String> read = storageManager.readFile(file);
            assertThat(read).isPresent();
            assertThat(read.get()).isEqualTo("new content");
        }
    }

    // ==================== 目录列表 ====================

    @Nested
    @DisplayName("listDirectory — 目录列表")
    class ListDirectory {

        @Test
        @DisplayName("列出目录应返回所有文件")
        void shouldListAllFiles() throws IOException {
            Files.createFile(tempDir.resolve("a.txt"));
            Files.createFile(tempDir.resolve("b.java"));
            Files.createFile(tempDir.resolve("c.md"));
            Files.createDirectory(tempDir.resolve("subdir"));

            List<FileInfo> files = storageManager.listDirectory(tempDir, false);
            assertThat(files).hasSize(4);
            assertThat(files).extracting(FileInfo::getName)
                    .contains("a.txt", "b.java", "c.md", "subdir");
        }

        @Test
        @DisplayName("文件应检测语言类型")
        void shouldDetectLanguage() throws IOException {
            Files.createFile(tempDir.resolve("test.java"));
            Files.createFile(tempDir.resolve("test.py"));
            Files.createFile(tempDir.resolve("test.md"));

            List<FileInfo> files = storageManager.listDirectory(tempDir, false);
            assertThat(files).extracting(FileInfo::getLanguage)
                    .contains("java", "python", "markdown");
        }

        @Test
        @DisplayName("目录应标记 isDirectory=true")
        void shouldMarkDirectories() throws IOException {
            Files.createFile(tempDir.resolve("file.txt"));
            Files.createDirectory(tempDir.resolve("folder"));

            List<FileInfo> files = storageManager.listDirectory(tempDir, false);
            FileInfo fileInfo = files.stream().filter(f -> f.getName().equals("file.txt")).findFirst().orElseThrow();
            FileInfo dirInfo = files.stream().filter(f -> f.getName().equals("folder")).findFirst().orElseThrow();

            assertThat(fileInfo.isDirectory()).isFalse();
            assertThat(dirInfo.isDirectory()).isTrue();
        }

        @Test
        @DisplayName("非目录路径应返回空列表")
        void shouldReturnEmptyForNonDirectory() throws IOException {
            Path file = tempDir.resolve("notadir.txt");
            Files.createFile(file);

            List<FileInfo> files = storageManager.listDirectory(file, false);
            assertThat(files).isEmpty();
        }

        @Test
        @DisplayName("递归列出应包含子目录文件")
        void shouldListRecursively() throws IOException {
            Path subdir = tempDir.resolve("sub");
            Files.createDirectory(subdir);
            Files.createFile(tempDir.resolve("root.txt"));
            Files.createFile(subdir.resolve("deep.txt"));

            List<FileInfo> files = storageManager.listDirectory(tempDir, true);
            assertThat(files).extracting(FileInfo::getName)
                    .contains("root.txt", "deep.txt", "sub");
        }
    }

    // ==================== 文件删除 ====================

    @Nested
    @DisplayName("deleteFile — 文件删除")
    class DeleteFile {

        @Test
        @DisplayName("删除文件后应不存在")
        void shouldDeleteFile() throws IOException {
            Path file = tempDir.resolve("delete_me.txt");
            Files.createFile(file);
            assertThat(Files.exists(file)).isTrue();

            boolean deleted = storageManager.deleteFile(file);
            assertThat(deleted).isTrue();
            assertThat(Files.exists(file)).isFalse();
        }

        @Test
        @DisplayName("删除不存在的文件应返回 false")
        void shouldReturnFalseForNonExistentFile() {
            Path file = tempDir.resolve("ghost.txt");
            boolean deleted = storageManager.deleteFile(file);
            assertThat(deleted).isFalse();
        }
    }

    // ==================== 文件存在检查 ====================

    @Nested
    @DisplayName("fileExists — 文件存在检查")
    class FileExists {

        @Test
        @DisplayName("存在的文件应返回 true")
        void shouldReturnTrueForExistingFile() throws IOException {
            Path file = tempDir.resolve("exists.txt");
            Files.createFile(file);

            assertThat(storageManager.fileExists(file.toString())).isTrue();
        }

        @Test
        @DisplayName("不存在的文件应返回 false")
        void shouldReturnFalseForNonExistentFile() {
            assertThat(storageManager.fileExists(tempDir.resolve("nope.txt").toString())).isFalse();
        }
    }

    // ==================== 存储配额 ====================

    @Nested
    @DisplayName("StorageQuota — 存储配额")
    class StorageQuotaTest {

        @Test
        @DisplayName("默认配额应有合理值")
        void shouldHaveReasonableDefaults() {
            StorageQuota quota = storageManager.getQuota();

            assertThat(quota.getMaxDiskUsageMB()).isGreaterThan(0);
            assertThat(quota.getMaxFileSizeBytes()).isGreaterThan(0);
            assertThat(quota.getRetentionDays()).isGreaterThan(0);
            assertThat(quota.isAutoCleanup()).isTrue();
        }

        @Test
        @DisplayName("配额阈值应在 0-1 之间")
        void shouldHaveValidThreshold() {
            StorageQuota quota = storageManager.getQuota();
            assertThat(quota.getCleanupThreshold()).isBetween(0.0, 1.0);
        }
    }

    // ==================== 存储统计 ====================

    @Nested
    @DisplayName("collectStats — 存储统计")
    class CollectStats {

        @Test
        @DisplayName("应能收集统计信息")
        void shouldCollectStats() {
            StorageStats stats = storageManager.collectStats();
            assertThat(stats).isNotNull();
            assertThat(stats.getCollectedAt()).isNotNull();
        }

        @Test
        @DisplayName("formatTotalUsage 应返回可读格式")
        void shouldFormatTotalUsage() {
            StorageStats stats = new StorageStats();
            stats.setTotalDiskUsageBytes(1024);
            stats.setTotalDbSizeBytes(512);

            assertThat(stats.formatTotalUsage()).isNotEmpty();
            assertThat(stats.getTotalUsageBytes()).isEqualTo(1536);
        }
    }

    // ==================== FileInfo 模型 ====================

    @Nested
    @DisplayName("FileInfo — 文件信息模型")
    class FileInfoTest {

        @Test
        @DisplayName("toString 应返回可读格式")
        void shouldFormatToString() {
            FileInfo info = new FileInfo("/path/to/App.java", "App.java", false, 2048, Instant.now());
            info.setLanguage("java");

            String str = info.toString();
            assertThat(str).contains("[FILE]");
            assertThat(str).contains("App.java");
            assertThat(str).contains("[java]");
        }

        @Test
        @DisplayName("目录 toString 应包含 [DIR] 标记")
        void shouldMarkDirectoryInToString() {
            FileInfo info = new FileInfo("/path/to/src", "src", true, 0, Instant.now());

            String str = info.toString();
            assertThat(str).contains("[DIR]");
            assertThat(str).contains("src");
        }

        @Test
        @DisplayName("formatSize 应正确格式化")
        void shouldFormatSizeCorrectly() {
            FileInfo small = new FileInfo("a.txt", "a.txt", false, 512, Instant.now());
            assertThat(small.toString()).contains("512 B");

            FileInfo kb = new FileInfo("b.txt", "b.txt", false, 2048, Instant.now());
            assertThat(kb.toString()).contains("2.0 KB");

            FileInfo mb = new FileInfo("c.txt", "c.txt", false, 2 * 1024 * 1024, Instant.now());
            assertThat(mb.toString()).contains("2.0 MB");
        }
    }

    // ==================== 清理 ====================

    @Nested
    @DisplayName("cleanup — 存储清理")
    class Cleanup {

        @Test
        @DisplayName("清理应正常完成（无异常）")
        void shouldCompleteWithoutError() {
            int cleaned = storageManager.cleanup();
            assertThat(cleaned).isGreaterThanOrEqualTo(0);
        }
    }
}