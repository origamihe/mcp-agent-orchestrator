package com.mcp.engine.test;

import com.mcp.engine.agent.ExecutionTracker;
import com.mcp.engine.agent.ExecutionTracker.ToolObservation;
import com.mcp.tools.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P0-Runtime-Security 验证 — 三个不变量测试。
 *
 * <pre>
 * P0-1: ToolCall ID Contract      → ToolCall.id == ToolResult.toolCallId
 * P0-2: Tool Authorization Fail-Closed → empty allowlist ≠ all tools
 * P0-3: Workspace Boundary         → real-path boundary validation
 * </pre>
 */
@DisplayName("P0-Runtime-Security — 三个不变量验证")
class P0RuntimeSecurityTest {

    // ============================================================
    // P0-1: ToolCall ID Contract
    // ============================================================

    @Nested
    @DisplayName("P0-1: ToolCall ID 执行契约")
    class P01_ToolCallIdContract {

        @Test
        @DisplayName("ToolResult.withToolCallId() 正确设置并返回 toolCallId")
        void shouldSetToolCallIdOnToolResult() {
            ToolResult result = ToolResult.success("文件读取成功", "/path/to/file.txt", "read")
                    .withToolCallId("tool-call-001");

            assertThat(result.toolCallId()).isEqualTo("tool-call-001");
            assertThat(result.success()).isTrue();
        }

        @Test
        @DisplayName("ToolResult.toJson() 包含 toolCallId 字段")
        void shouldIncludeToolCallIdInJson() {
            ToolResult result = ToolResult.success("操作成功")
                    .withToolCallId("tool-call-001");

            String json = result.toJson();
            assertThat(json).contains("\"toolCallId\":\"tool-call-001\"");
        }

        @Test
        @DisplayName("ToolResult.toJson() 无 toolCallId 时不包含字段")
        void shouldNotIncludeToolCallIdInJsonWhenNull() {
            ToolResult result = ToolResult.success("操作成功");

            String json = result.toJson();
            assertThat(json).doesNotContain("toolCallId");
        }

        @Test
        @DisplayName("ToolResult.failure() 也支持 toolCallId")
        void shouldSupportToolCallIdOnFailure() {
            ToolResult result = ToolResult.failure("文件不存在")
                    .withToolCallId("tool-call-002");

            assertThat(result.toolCallId()).isEqualTo("tool-call-002");
            assertThat(result.success()).isFalse();
        }

        @Test
        @DisplayName("ToolResult.withData() 保留 toolCallId")
        void shouldPreserveToolCallIdOnWithData() {
            ToolResult result = ToolResult.success("结果")
                    .withToolCallId("tool-call-003")
                    .withData("some data");

            assertThat(result.toolCallId()).isEqualTo("tool-call-003");
            assertThat(result.data()).isEqualTo("some data");
        }

        @Test
        @DisplayName("ToolObservation 记录 toolCallId 匹配 ToolCall.id")
        void shouldRecordToolCallIdInObservation() {
            ExecutionTracker tracker = new ExecutionTracker();
            String expectedId = "call-abc-123";

            tracker.recordToolCall("read_file", "{}", true, "ok", null, 100, expectedId);

            List<ToolObservation> observations = tracker.getObservations();
            assertThat(observations).hasSize(1);
            assertThat(observations.get(0).toolCallId()).isEqualTo(expectedId);
        }

        @Test
        @DisplayName("ToolObservation 无 toolCallId 时值为 null")
        void shouldAllowNullToolCallIdInObservation() {
            ExecutionTracker tracker = new ExecutionTracker();

            tracker.recordToolCall("read_file", "{}", true, "ok", null, 100);

            ToolObservation obs = tracker.getObservations().get(0);
            assertThat(obs.toolCallId()).isNull();
        }

        @Test
        @DisplayName("ToolCall ID 通过 ExecutionTracker 传递不变")
        void shouldPreserveToolCallIdThroughExecutionTracker() {
            ExecutionTracker tracker = new ExecutionTracker();
            String id = "call-xyz-789";

            tracker.recordToolCall("tool_a", "{}", true, "ok", null, 50, id);
            tracker.recordToolCall("tool_b", "{}", false, "", "err", 100, id);

            assertThat(tracker.getObservations())
                    .allMatch(o -> id.equals(o.toolCallId()));
        }
    }

    // ============================================================
    // P0-2: Tool Authorization Fail-Closed
    // ============================================================

    @Nested
    @DisplayName("P0-2: Tool 权限 Fail-Closed")
    class P02_AuthorizationFailClosed {

        @Test
        @DisplayName("当 filteredTools 为空时，应返回空列表而非全部工具")
        void shouldReturnEmptyListWhenNoToolsAuthorized() {
            List<String> allowedToolNames = List.of("tool_a");
            List<String> registerToolNames = List.of("tool_b", "tool_c");
            List<String> filteredTools = filterTools(allowedToolNames, registerToolNames);

            assertThat(filteredTools).isEmpty();
        }

        @Test
        @DisplayName("当 allowlist 为空时，应返回空列表")
        void shouldReturnEmptyListWhenAllowlistEmpty() {
            List<String> allowedToolNames = List.of();
            List<String> registerToolNames = List.of("tool_a", "tool_b");
            List<String> filteredTools = filterTools(allowedToolNames, registerToolNames);

            assertThat(filteredTools).isEmpty();
        }

        @Test
        @DisplayName("当 allowlist 与 registry 部分匹配时，仅返回匹配的")
        void shouldReturnOnlyMatchingTools() {
            List<String> allowedToolNames = List.of("tool_a", "tool_c");
            List<String> registerToolNames = List.of("tool_a", "tool_b", "tool_c", "tool_d");
            List<String> filteredTools = filterTools(allowedToolNames, registerToolNames);

            assertThat(filteredTools).containsExactly("tool_a", "tool_c");
        }

        @Test
        @DisplayName("当 allowlist 与 registry 完全匹配时，返回全部")
        void shouldReturnAllWhenAllMatch() {
            List<String> allowedToolNames = List.of("tool_a", "tool_b");
            List<String> registerToolNames = List.of("tool_a", "tool_b");
            List<String> filteredTools = filterTools(allowedToolNames, registerToolNames);

            assertThat(filteredTools).containsExactly("tool_a", "tool_b");
        }

        @Test
        @DisplayName("空允许列表永远不会等于全部工具")
        void shouldNeverFallbackToAllTools() {
            List<String> testAllowlist = List.of("nonexistent_tool");
            List<String> registry = List.of("real_tool_1", "real_tool_2", "real_tool_3");
            List<String> result = filterTools(testAllowlist, registry);

            assertThat(result).isEmpty();
            assertThat(result).isNotEqualTo(registry);
        }

        private List<String> filterTools(List<String> allowedToolNames, List<String> registerToolNames) {
            return registerToolNames.stream()
                    .filter(allowedToolNames::contains)
                    .toList();
        }
    }

    // ============================================================
    // P0-3: Workspace Boundary
    // ============================================================

    @Nested
    @DisplayName("P0-3: Workspace 安全边界")
    class P03_WorkspaceBoundary {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("../ 路径遍历应被拒绝")
        void shouldRejectPathTraversal() throws Exception {
            Path workspaceRoot = tempDir.resolve("workspace");
            Files.createDirectories(workspaceRoot);

            com.mcp.tools.sandbox.WorkspaceSandbox sandbox =
                    new com.mcp.tools.sandbox.WorkspaceSandbox(workspaceRoot);

            Path result = sandbox.resolve(Path.of("../../../etc/passwd"));
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("绝对路径越界应被拒绝")
        void shouldRejectAbsolutePathOutsideWorkspace() throws Exception {
            Path workspaceRoot = tempDir.resolve("workspace");
            Files.createDirectories(workspaceRoot);

            com.mcp.tools.sandbox.WorkspaceSandbox sandbox =
                    new com.mcp.tools.sandbox.WorkspaceSandbox(workspaceRoot);

            Path outsidePath = tempDir.resolve("outside").resolve("secret.txt");
            Files.createDirectories(outsidePath.getParent());
            Files.writeString(outsidePath, "secret");

            Path result = sandbox.resolve(outsidePath);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Workspace 内正常文件可以访问")
        void shouldAllowNormalFileInsideWorkspace() throws Exception {
            Path workspaceRoot = tempDir.resolve("workspace");
            Files.createDirectories(workspaceRoot);
            Path legitimateFile = workspaceRoot.resolve("legit.txt");
            Files.writeString(legitimateFile, "hello");

            com.mcp.tools.sandbox.WorkspaceSandbox sandbox =
                    new com.mcp.tools.sandbox.WorkspaceSandbox(workspaceRoot);

            Path result = sandbox.resolve(Path.of("legit.txt"));
            assertThat(result).isNotNull();
            assertThat(result.toRealPath()).isEqualTo(legitimateFile.toRealPath());
        }

        @Test
        @DisplayName("符号链接越界应被拒绝（checkNoSymlink）")
        void shouldRejectSymlink() throws Exception {
            Path workspaceRoot = tempDir.resolve("workspace");
            Files.createDirectories(workspaceRoot);

            Path outsideFile = tempDir.resolve("outside_secret.txt");
            Files.writeString(outsideFile, "sensitive data");

            Path linkInWorkspace = workspaceRoot.resolve("bad_link");
            Files.createSymbolicLink(linkInWorkspace, outsideFile);

            com.mcp.tools.sandbox.WorkspaceSandbox sandbox =
                    new com.mcp.tools.sandbox.WorkspaceSandbox(workspaceRoot);

            assertThatThrownBy(() -> sandbox.checkNoSymlink(linkInWorkspace))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Symlinks are not allowed");
        }

        @Test
        @DisplayName("符号链接内的文件读取应被 WorkspaceSandbox 检测（toRealPath）")
        void shouldDetectSymlinkBypass() throws Exception {
            Path workspaceRoot = tempDir.resolve("workspace");
            Files.createDirectories(workspaceRoot);

            Path outsideFile = tempDir.resolve("outside_secret.txt");
            Files.writeString(outsideFile, "sensitive data");

            Path linkInWorkspace = workspaceRoot.resolve("bad_link");
            Files.createSymbolicLink(linkInWorkspace, outsideFile);

            com.mcp.tools.sandbox.WorkspaceSandbox sandbox =
                    new com.mcp.tools.sandbox.WorkspaceSandbox(workspaceRoot);

            boolean readAllowed = sandbox.isReadAllowed(linkInWorkspace);
            assertThat(readAllowed).isFalse();
        }

        @Test
        @DisplayName("WorkspaceFileService 应拒绝符号链接文件读取")
        void shouldRejectSymlinkInFileService() throws Exception {
            Path workspaceRoot = tempDir.resolve("workspace");
            Files.createDirectories(workspaceRoot);

            Path normalFile = workspaceRoot.resolve("normal.txt");
            Files.writeString(normalFile, "hello world");

            Path outsideFile = tempDir.resolve("outside_secret.txt");
            Files.writeString(outsideFile, "sensitive");

            Path linkInWorkspace = workspaceRoot.resolve("bad_link");
            Files.createSymbolicLink(linkInWorkspace, outsideFile);

            com.mcp.tools.service.WorkspaceFileService fileService =
                    new com.mcp.tools.service.WorkspaceFileService(workspaceRoot.toString());

            String content = fileService.readAll(normalFile);
            assertThat(content).isEqualTo("hello world");

            assertThatThrownBy(() -> fileService.readAll(linkInWorkspace))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Symlinks are not allowed");
        }

        @Test
        @DisplayName("文件不存在时父目录越界应被拒绝")
        void shouldRejectNonExistentFileOutsideWorkspace() throws Exception {
            Path workspaceRoot = tempDir.resolve("workspace");
            Files.createDirectories(workspaceRoot);

            com.mcp.tools.sandbox.WorkspaceSandbox sandbox =
                    new com.mcp.tools.sandbox.WorkspaceSandbox(workspaceRoot);

            Path nonExistentOutside = workspaceRoot.resolve("../../etc/nonexistent.txt");
            Path result = sandbox.resolve(nonExistentOutside);
            assertThat(result).isNull();
        }
    }
}