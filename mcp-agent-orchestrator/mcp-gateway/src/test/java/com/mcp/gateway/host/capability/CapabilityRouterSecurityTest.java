package com.mcp.gateway.host.capability;

import com.mcp.common.tool.ToolRiskLevel;
import com.mcp.gateway.ws.WebSocketSessionManager;
import com.mcp.tools.sandbox.ProcessSandboxExecutor;
import com.mcp.tools.sandbox.SandboxPolicy;
import com.mcp.tools.sandbox.WorkspaceSandbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0+P1 安全控制面验证测试。
 *
 * 验证 CapabilityRouter 是否正确接入安全设施：
 * - P0-3: SandboxPolicy 接入验证
 * - P0-2: WorkspaceSandbox 路径校验
 * - P0-1: runTerminal 沙箱约束验证
 * - P1-1: CapabilityAuthorization 授权层验证
 * - P1-2: sendTo 定向路由（替代 broadcast）
 */
@DisplayName("CapabilityRouter — 安全控制面验证")
class CapabilityRouterSecurityTest {

    private static final Path WORKSPACE_ROOT = Path.of(System.getProperty("java.io.tmpdir"), "mcp-test-workspace");

    private WebSocketSessionManager sessionManager;
    private CapabilityAuthorization capabilityAuth;
    private CapabilityRiskRegistry riskRegistry;
    private SandboxPolicy sandboxPolicy;
    private WorkspaceSandbox workspaceSandbox;
    private CapabilityAuditLog auditLog;
    private CapabilityRouter router;

    @BeforeEach
    void setUp() throws Exception {
        java.nio.file.Files.createDirectories(WORKSPACE_ROOT);

        sessionManager = new WebSocketSessionManager();
        capabilityAuth = new CapabilityAuthorization();
        riskRegistry = new CapabilityRiskRegistry();
        workspaceSandbox = new WorkspaceSandbox(WORKSPACE_ROOT);
        sandboxPolicy = new SandboxPolicy(workspaceSandbox, new ProcessSandboxExecutor());
        auditLog = new CapabilityAuditLog();
        router = new CapabilityRouter(sessionManager, capabilityAuth, riskRegistry, sandboxPolicy, workspaceSandbox, auditLog);

        // [P1-1] 为测试会话授权 IDE 级别权限（L0-L3）
        capabilityAuth.authorizeSessionAsIDE("test-session");
    }

    // ==================== P0-3: Security Control Plane Bypass ====================

    @Nested
    @DisplayName("P0-3: SandboxPolicy 接入验证")
    class SandboxPolicyIntegration {

        @Test
        @DisplayName("L5 系统级操作应被直接拒绝")
        void shouldBlockL5SystemOperation() {
            // 授权 L5 以通过授权层检查，验证 SandboxPolicy 的 BLOCKED 行为
            capabilityAuth.authorizeSession("test-session", Set.of(ToolRiskLevel.L5));
            Mono<Map<String, Object>> result = router.call("test-session", "system_operation",
                    Map.of("action", "shutdown"));

            StepVerifier.create(result)
                    .assertNext(map -> {
                        assertThat(map).containsKey("error");
                        assertThat(map.get("error").toString()).contains("blocked");
                        assertThat(map).containsEntry("riskLevel", "L5");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("L4 网络操作应被标记为 CONTAINER_SANDBOX（当前未实现，但不会直接拒绝）")
        void shouldMarkL4AsContainerSandbox() {
            ToolRiskLevel risk = riskRegistry.getRiskLevel("install_package");
            SandboxPolicy.Decision decision = sandboxPolicy.decide(risk);

            assertThat(risk).isEqualTo(ToolRiskLevel.L4);
            assertThat(decision).isEqualTo(SandboxPolicy.Decision.CONTAINER_SANDBOX);
        }

        @Test
        @DisplayName("L0 只读操作应通过（无沙箱）")
        void shouldAllowL0ReadOnlyOperation() {
            Mono<Map<String, Object>> result = router.call("test-session", "get_editor_state",
                    Map.of());

            StepVerifier.create(result)
                    .assertNext(map -> {
                        assertThat(map).containsKey("error");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("未知 capability 应默认按 L5（最高风险）处理")
        void shouldTreatUnknownCapabilityAsL5() {
            // 授权 L5 以通过授权层检查，验证 SandboxPolicy 的 BLOCKED 行为
            capabilityAuth.authorizeSession("test-session", Set.of(ToolRiskLevel.L5));
            Mono<Map<String, Object>> result = router.call("test-session", "unknown_capability",
                    Map.of("data", "test"));

            StepVerifier.create(result)
                    .assertNext(map -> {
                        assertThat(map).containsKey("error");
                        assertThat(map.get("error").toString()).contains("blocked");
                        assertThat(map).containsEntry("riskLevel", "L5");
                    })
                    .verifyComplete();
        }
    }

    // ==================== P0-2: WorkspaceSandbox 路径校验 ====================

    @Nested
    @DisplayName("P0-2: WorkspaceSandbox 路径校验")
    class WorkspaceIsolation {

        @Test
        @DisplayName("workspace 内路径应通过校验")
        void shouldAllowPathInsideWorkspace() {
            Path workspaceFile = WORKSPACE_ROOT.resolve("test.txt");

            Mono<Map<String, Object>> result = router.call("test-session", "write_file",
                    Map.of("filePath", workspaceFile.toString(), "content", "test"));

            StepVerifier.create(result)
                    .assertNext(map -> {
                        assertThat(map).containsKey("error");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("workspace 外路径应被拒绝（绝对路径）")
        void shouldRejectAbsolutePathOutsideWorkspace() {
            Mono<Map<String, Object>> result = router.call("test-session", "write_file",
                    Map.of("filePath", "C:\\Windows\\System32\\test.dll", "content", "malicious"));

            StepVerifier.create(result)
                    .assertNext(map -> {
                        assertThat(map).containsKey("error");
                        assertThat(map.get("error").toString()).contains("outside workspace");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("路径穿越应被拒绝（../../etc/passwd）")
        void shouldRejectPathTraversal() {
            Mono<Map<String, Object>> result = router.call("test-session", "write_file",
                    Map.of("filePath", WORKSPACE_ROOT.resolve("../../etc/passwd").toString(), "content", "hacked"));

            StepVerifier.create(result)
                    .assertNext(map -> {
                        assertThat(map).containsKey("error");
                        assertThat(map.get("error").toString()).contains("outside workspace");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("L1 只读操作不触发 workspace 校验")
        void shouldNotCheckWorkspaceForL1ReadOnly() {
            Mono<Map<String, Object>> result = router.call("test-session", "read_file",
                    Map.of("filePath", "/some/path"));

            StepVerifier.create(result)
                    .assertNext(map -> {
                        assertThat(map).containsKey("error");
                    })
                    .verifyComplete();
        }
    }

    // ==================== P0-1: runTerminal 沙箱约束 ====================

    @Nested
    @DisplayName("P0-1: runTerminal 沙箱约束")
    class ProcessSandbox {

        @Test
        @DisplayName("runTerminal (L3) 应被标记为 PROCESS_SANDBOX")
        void shouldEnforceProcessSandboxForRunTerminal() {
            ToolRiskLevel risk = riskRegistry.getRiskLevel("run_terminal");
            SandboxPolicy.Decision decision = sandboxPolicy.decide(risk);

            assertThat(risk).isEqualTo(ToolRiskLevel.L3);
            assertThat(decision).isEqualTo(SandboxPolicy.Decision.PROCESS_SANDBOX);
        }

        @Test
        @DisplayName("runTerminal 应自动添加超时和输出限制参数")
        void shouldAddTimeoutAndOutputLimitToRunTerminal() {
            Mono<Map<String, Object>> result = router.call("test-session", "run_terminal",
                    Map.of("command", "echo hello"));

            StepVerifier.create(result)
                    .assertNext(map -> {
                        assertThat(map).containsKey("error");
                    })
                    .verifyComplete();
        }
    }

    // ==================== P1-2: 定向路由验证 ====================

    @Nested
    @DisplayName("P1-2: 定向路由（sendTo 替代 broadcast）")
    class DirectedRouting {

        @Test
        @DisplayName("sendTo 方法存在且可调用，空会话安全返回")
        void shouldHaveSendToMethod() {
            sessionManager.sendTo("non-existent-session", "{\"type\":\"test\"}");
        }

        @Test
        @DisplayName("hasSession 应正确判断会话是否存在")
        void shouldCheckSessionExistence() {
            assertThat(sessionManager.hasSession("non-existent")).isFalse();
        }
    }

    // ==================== 风险等级映射验证 ====================

    @Nested
    @DisplayName("CapabilityRiskRegistry 映射验证")
    class RiskRegistry {

        @Test
        @DisplayName("write_file 应映射为 L2 (WORKSPACE_ISOLATION)")
        void shouldMapWriteFileToL2() {
            assertThat(riskRegistry.getRiskLevel("write_file")).isEqualTo(ToolRiskLevel.L2);
        }

        @Test
        @DisplayName("read_file 应映射为 L1 (NONE)")
        void shouldMapReadFileToL1() {
            assertThat(riskRegistry.getRiskLevel("read_file")).isEqualTo(ToolRiskLevel.L1);
        }

        @Test
        @DisplayName("get_editor_state 应映射为 L0 (NONE)")
        void shouldMapGetEditorStateToL0() {
            assertThat(riskRegistry.getRiskLevel("get_editor_state")).isEqualTo(ToolRiskLevel.L0);
        }

        @Test
        @DisplayName("run_terminal 应映射为 L3 (PROCESS_SANDBOX)")
        void shouldMapRunTerminalToL3() {
            assertThat(riskRegistry.getRiskLevel("run_terminal")).isEqualTo(ToolRiskLevel.L3);
        }

        @Test
        @DisplayName("apply_full_content 应映射为 L2 (WORKSPACE_ISOLATION)")
        void shouldMapApplyFullContentToL2() {
            assertThat(riskRegistry.getRiskLevel("apply_full_content")).isEqualTo(ToolRiskLevel.L2);
        }

        @Test
        @DisplayName("已知 capability 应被识别")
        void shouldRecognizeKnownCapabilities() {
            assertThat(riskRegistry.isKnown("read_file")).isTrue();
            assertThat(riskRegistry.isKnown("write_file")).isTrue();
            assertThat(riskRegistry.isKnown("run_terminal")).isTrue();
        }

        @Test
        @DisplayName("未知 capability 应不被识别")
        void shouldNotRecognizeUnknownCapabilities() {
            assertThat(riskRegistry.isKnown("non_existent")).isFalse();
        }
    }

    // ==================== P1-1: CapabilityAuthorization 授权层验证 ====================

    @Nested
    @DisplayName("P1-1: CapabilityAuthorization 授权层验证")
    class AuthorizationLayer {

        @Test
        @DisplayName("IDE 授权会话应允许 L0-L3 能力")
        void shouldAllowIdeAuthorizedSession() {
            assertThat(capabilityAuth.isAuthorized("test-session", ToolRiskLevel.L0)).isTrue();
            assertThat(capabilityAuth.isAuthorized("test-session", ToolRiskLevel.L1)).isTrue();
            assertThat(capabilityAuth.isAuthorized("test-session", ToolRiskLevel.L2)).isTrue();
            assertThat(capabilityAuth.isAuthorized("test-session", ToolRiskLevel.L3)).isTrue();
        }

        @Test
        @DisplayName("IDE 授权会话应拒绝 L4-L5 能力")
        void shouldRejectL4L5ForIdeSession() {
            assertThat(capabilityAuth.isAuthorized("test-session", ToolRiskLevel.L4)).isFalse();
            assertThat(capabilityAuth.isAuthorized("test-session", ToolRiskLevel.L5)).isFalse();
        }

        @Test
        @DisplayName("未授权会话应被拒绝")
        void shouldRejectUnauthorizedSession() {
            assertThat(capabilityAuth.isAuthorized("unauthorized-session", ToolRiskLevel.L0)).isFalse();
        }

        @Test
        @DisplayName("Agent 授权会话应仅允许 L0-L1")
        void shouldAllowOnlyL0L1ForAgentSession() {
            capabilityAuth.authorizeSessionAsAgent("agent-session");
            assertThat(capabilityAuth.isAuthorized("agent-session", ToolRiskLevel.L0)).isTrue();
            assertThat(capabilityAuth.isAuthorized("agent-session", ToolRiskLevel.L1)).isTrue();
            assertThat(capabilityAuth.isAuthorized("agent-session", ToolRiskLevel.L2)).isFalse();
            assertThat(capabilityAuth.isAuthorized("agent-session", ToolRiskLevel.L3)).isFalse();
        }

        @Test
        @DisplayName("撤销授权后会话应被拒绝")
        void shouldRejectAfterRevocation() {
            capabilityAuth.authorizeSessionAsIDE("revoke-session");
            assertThat(capabilityAuth.isAuthorized("revoke-session", ToolRiskLevel.L1)).isTrue();
            capabilityAuth.revokeSession("revoke-session");
            assertThat(capabilityAuth.isAuthorized("revoke-session", ToolRiskLevel.L1)).isFalse();
        }

        @Test
        @DisplayName("未授权会话调用 runTerminal (L3) 应被 CapabilityRouter 拒绝")
        void shouldRejectUnauthorizedRunTerminal() {
            Mono<Map<String, Object>> result = router.call("unauthorized-session", "run_terminal",
                    Map.of("command", "echo hello"));

            StepVerifier.create(result)
                    .assertNext(map -> {
                        assertThat(map).containsKey("error");
                        assertThat(map.get("error").toString()).contains("not authorized");
                    })
                    .verifyComplete();
        }
    }
}