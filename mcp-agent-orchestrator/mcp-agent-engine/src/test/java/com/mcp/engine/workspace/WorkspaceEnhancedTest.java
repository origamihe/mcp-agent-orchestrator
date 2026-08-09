package com.mcp.engine.workspace;

import com.mcp.common.workspace.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 验证 — Workspace 增强模型测试。
 * 验证：
 * 1. openFile 正确追踪文件（auto-detect language + readme）
 * 2. trackReadme 正确标记 README
 * 3. closeFile 正确移除文件
 * 4. getLastOpenedFile / getOpenedReadme 正确性
 * 5. projectRoot 字段
 * 6. buildWorkspacePrompt 包含增强信息
 * 7. OpenedFile.toReferenceString 正确性
 * 8. detectLanguage / detectIsReadme 正确性
 */
@DisplayName("Workspace — P1 增强测试")
class WorkspaceEnhancedTest {

    private Workspace workspace;

    @BeforeEach
    void setUp() {
        workspace = new Workspace();
        workspace.setWorkspaceId("test-workspace");
        workspace.setName("Test Project");
    }

    @Nested
    @DisplayName("projectRoot")
    class ProjectRoot {

        @Test
        @DisplayName("设置 projectRoot 后应在 Prompt 中显示")
        void shouldDisplayProjectRootInPrompt() {
            workspace.setProjectRoot("C:\\projects\\myapp");
            workspace.setProjectPath("C:\\projects\\myapp\\src");

            String prompt = workspace.buildWorkspacePrompt();
            assertThat(prompt).contains("项目根目录：C:\\projects\\myapp");
        }

        @Test
        @DisplayName("无 projectRoot 时应回退到 projectPath")
        void shouldFallbackToProjectPath() {
            workspace.setProjectPath("C:\\projects\\myapp");

            String prompt = workspace.buildWorkspacePrompt();
            assertThat(prompt).contains("项目路径：C:\\projects\\myapp");
        }
    }

    @Nested
    @DisplayName("openFile — 打开文件追踪")
    class OpenFile {

        @Test
        @DisplayName("打开 Java 文件应自动检测 language=java")
        void shouldDetectJavaLanguage() {
            workspace.openFile(
                    "src/main/java/com/example/App.java",
                    "public class App {}",
                    "UTF-8",
                    Instant.now(),
                    20
            );

            Optional<Workspace.OpenedFile> file = workspace.getOpenedFile("src/main/java/com/example/App.java");
            assertThat(file).isPresent();
            assertThat(file.get().getLanguage()).isEqualTo("java");
            assertThat(file.get().isReadme()).isFalse();
        }

        @Test
        @DisplayName("打开 Python 文件应自动检测 language=python")
        void shouldDetectPythonLanguage() {
            workspace.openFile("main.py", "print('hello')", "UTF-8", Instant.now(), 16);

            Optional<Workspace.OpenedFile> file = workspace.getOpenedFile("main.py");
            assertThat(file).isPresent();
            assertThat(file.get().getLanguage()).isEqualTo("python");
        }

        @Test
        @DisplayName("打开文件应更新 lastActiveFile")
        void shouldUpdateLastActiveFile() {
            workspace.openFile("test.java", "code", "UTF-8", Instant.now(), 4);

            assertThat(workspace.getLastActiveFile()).isEqualTo("test.java");
        }

        @Test
        @DisplayName("打开文件应更新 lastOpenedFile")
        void shouldUpdateLastOpenedFile() {
            workspace.openFile("test.md", "# Title", "UTF-8", Instant.now(), 7);

            Optional<Workspace.OpenedFile> lastOpened = workspace.getLastOpenedFile();
            assertThat(lastOpened).isPresent();
            assertThat(lastOpened.get().getPath()).isEqualTo("test.md");
        }

        @Test
        @DisplayName("打开文件应更新 lastActiveAt")
        void shouldUpdateLastActiveAt() {
            Instant before = Instant.now();
            workspace.openFile("test.txt", "content", "UTF-8", Instant.now(), 7);

            assertThat(workspace.getLastActiveAt()).isNotNull();
            assertThat(workspace.getLastActiveAt()).isAfterOrEqualTo(before);
        }

        @Test
        @DisplayName("打开 Markdown 文件应检测 language=markdown")
        void shouldDetectMarkdown() {
            workspace.openFile("README.md", "# README", "UTF-8", Instant.now(), 8);

            Optional<Workspace.OpenedFile> file = workspace.getOpenedFile("README.md");
            assertThat(file).isPresent();
            assertThat(file.get().getLanguage()).isEqualTo("markdown");
        }

        @Test
        @DisplayName("打开 SQL 文件应检测 language=sql")
        void shouldDetectSQL() {
            workspace.openFile("schema.sql", "SELECT 1", "UTF-8", Instant.now(), 8);

            Optional<Workspace.OpenedFile> file = workspace.getOpenedFile("schema.sql");
            assertThat(file).isPresent();
            assertThat(file.get().getLanguage()).isEqualTo("sql");
        }
    }

    @Nested
    @DisplayName("trackReadme — README 追踪")
    class TrackReadme {

        @Test
        @DisplayName("trackReadme 应自动标记 isReadme=true")
        void shouldMarkAsReadme() {
            workspace.trackReadme("README.md", "# My Project\nThis is a test project.");

            Optional<Workspace.OpenedFile> readme = workspace.getOpenedReadme();
            assertThat(readme).isPresent();
            assertThat(readme.get().isReadme()).isTrue();
            assertThat(readme.get().getPath()).isEqualTo("README.md");
        }

        @Test
        @DisplayName("trackReadme 应自动设置 language=markdown")
        void shouldSetLanguageToMarkdown() {
            workspace.trackReadme("README.md", "# README");

            Optional<Workspace.OpenedFile> readme = workspace.getOpenedReadme();
            assertThat(readme).isPresent();
            assertThat(readme.get().getLanguage()).isEqualTo("markdown");
        }

        @Test
        @DisplayName("trackReadme 应更新 lastOpenedFile")
        void shouldUpdateLastOpenedFile() {
            workspace.trackReadme("README.md", "# README");

            Optional<Workspace.OpenedFile> lastOpened = workspace.getLastOpenedFile();
            assertThat(lastOpened).isPresent();
            assertThat(lastOpened.get().isReadme()).isTrue();
        }
    }

    @Nested
    @DisplayName("closeFile — 关闭文件")
    class CloseFile {

        @Test
        @DisplayName("closeFile 应从 openedFiles 中移除")
        void shouldRemoveFromOpenedFiles() {
            workspace.openFile("test.java", "code", "UTF-8", Instant.now(), 4);
            assertThat(workspace.getOpenedFiles()).hasSize(1);

            workspace.closeFile("test.java");
            assertThat(workspace.getOpenedFiles()).isEmpty();
        }

        @Test
        @DisplayName("closeFile 关闭 lastActiveFile 时应清空 lastActiveFile")
        void shouldClearLastActiveFile() {
            workspace.openFile("test.java", "code", "UTF-8", Instant.now(), 4);
            assertThat(workspace.getLastActiveFile()).isEqualTo("test.java");

            workspace.closeFile("test.java");
            assertThat(workspace.getLastActiveFile()).isNull();
        }
    }

    @Nested
    @DisplayName("detectIsReadme — README 检测")
    class DetectIsReadme {

        @Test
        @DisplayName("README.md 应检测为 README")
        void shouldDetectReadmeMd() {
            workspace.openFile("README.md", "# README", "UTF-8", Instant.now(), 8);
            assertThat(workspace.getOpenedFile("README.md").get().isReadme()).isTrue();
        }

        @Test
        @DisplayName("readme.txt 应检测为 README")
        void shouldDetectReadmeTxt() {
            workspace.openFile("readme.txt", "readme", "UTF-8", Instant.now(), 6);
            assertThat(workspace.getOpenedFile("readme.txt").get().isReadme()).isTrue();
        }

        @Test
        @DisplayName("README_zh.md 应检测为 README")
        void shouldDetectReadmeZh() {
            workspace.openFile("README_zh.md", "# 中文 README", "UTF-8", Instant.now(), 12);
            assertThat(workspace.getOpenedFile("README_zh.md").get().isReadme()).isTrue();
        }

        @Test
        @DisplayName("subdir/README.md 应检测为 README")
        void shouldDetectReadmeInSubdir() {
            workspace.openFile("docs/README.md", "# Docs", "UTF-8", Instant.now(), 6);
            assertThat(workspace.getOpenedFile("docs/README.md").get().isReadme()).isTrue();
        }

        @Test
        @DisplayName("普通文件不应检测为 README")
        void shouldNotDetectNormalFile() {
            workspace.openFile("main.java", "code", "UTF-8", Instant.now(), 4);
            assertThat(workspace.getOpenedFile("main.java").get().isReadme()).isFalse();
        }
    }

    @Nested
    @DisplayName("buildWorkspacePrompt — Prompt 渲染")
    class BuildWorkspacePrompt {

        @Test
        @DisplayName("README 文件应在 Prompt 中标记")
        void shouldMarkReadmeInPrompt() {
            workspace.setProjectRoot("C:\\projects\\myapp");
            workspace.trackReadme("README.md", "# My Project");

            String prompt = workspace.buildWorkspacePrompt();
            assertThat(prompt).contains("README.md");
            assertThat(prompt).contains("(README)");
            assertThat(prompt).contains("[markdown]");
        }

        @Test
        @DisplayName("应显示上次打开的文件引用")
        void shouldShowLastOpenedFile() {
            workspace.openFile("src/App.java", "public class App {}", "UTF-8", Instant.now(), 20);

            String prompt = workspace.buildWorkspacePrompt();
            assertThat(prompt).contains("上次打开的文件");
            assertThat(prompt).contains("src/App.java");
            assertThat(prompt).contains("[java]");
        }

        @Test
        @DisplayName("空 workspace 应返回空 Prompt")
        void shouldReturnEmptyPromptForEmptyWorkspace() {
            Workspace emptyWs = new Workspace();
            assertThat(emptyWs.buildWorkspacePrompt()).isEmpty();
        }
    }

    @Nested
    @DisplayName("OpenedFile.toReferenceString")
    class ToReferenceString {

        @Test
        @DisplayName("README 文件引用应包含标记")
        void shouldIncludeReadmeMark() {
            Workspace.OpenedFile file = new Workspace.OpenedFile("# README", "UTF-8", Instant.now(), 8);
            file.setPath("README.md");
            file.setLanguage("markdown");
            file.setReadme(true);
            file.setReadAt(Instant.now());

            String ref = file.toReferenceString();
            assertThat(ref).contains("README.md");
            assertThat(ref).contains("(README)");
            assertThat(ref).contains("[markdown]");
        }

        @Test
        @DisplayName("普通文件引用不包含 README 标记")
        void shouldNotIncludeReadmeMark() {
            Workspace.OpenedFile file = new Workspace.OpenedFile("code", "UTF-8", Instant.now(), 4);
            file.setPath("App.java");
            file.setLanguage("java");
            file.setReadAt(Instant.now());

            String ref = file.toReferenceString();
            assertThat(ref).contains("App.java");
            assertThat(ref).doesNotContain("(README)");
            assertThat(ref).contains("[java]");
        }
    }

    @Nested
    @DisplayName("detectLanguage — 语言检测")
    class DetectLanguage {

        @Test
        @DisplayName("应检测 10+ 种常见语言")
        void shouldDetectCommonLanguages() {
            assertFileLanguage("App.java", "java");
            assertFileLanguage("main.py", "python");
            assertFileLanguage("index.js", "javascript");
            assertFileLanguage("app.ts", "typescript");
            assertFileLanguage("main.go", "go");
            assertFileLanguage("lib.rs", "rust");
            assertFileLanguage("query.sql", "sql");
            assertFileLanguage("readme.md", "markdown");
            assertFileLanguage("config.json", "json");
            assertFileLanguage("pom.xml", "xml");
            assertFileLanguage("docker-compose.yaml", "yaml");
            assertFileLanguage("index.html", "html");
            assertFileLanguage("style.css", "css");
            assertFileLanguage("setup.sh", "shell");
            assertFileLanguage("application.properties", "properties");
            assertFileLanguage("build.gradle", "gradle");
        }

        private void assertFileLanguage(String path, String expectedLanguage) {
            workspace.openFile(path, "content", "UTF-8", Instant.now(), 7);
            assertThat(workspace.getOpenedFile(path).get().getLanguage())
                    .as("Language for %s", path)
                    .isEqualTo(expectedLanguage);
        }
    }
}