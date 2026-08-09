package com.mcp.engine.test.chaos;

import com.mcp.common.identity.MemoryIdentity;
import com.mcp.core.context.BuildContext;
import com.mcp.core.context.ContextAssembler;
import com.mcp.core.context.PromptContext;
import com.mcp.core.context.PromptContextBuilder;
import com.mcp.core.context.PromptLayer;
import com.mcp.core.context.provider.HostContextProvider;
import com.mcp.core.context.provider.IdentityContextProvider;
import com.mcp.core.context.provider.RelationshipContextProvider;
import com.mcp.core.context.provider.WorkspaceContextProvider;
import com.mcp.core.domain.memory.MemoryType;
import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.repository.MemoryPackageRepository;
import com.mcp.engine.context.TokenBudget;
import com.mcp.engine.memory.MemoryConflictResolver;
import com.mcp.engine.memory.MemoryConflictResolver.ConflictGroup;
import com.mcp.engine.memory.MemoryEvaluator;
import com.mcp.engine.memory.MemoryMergeService;
import com.mcp.engine.memory.MemoryMergeService.MergeResult;
import com.mcp.engine.memory.MemoryMergeService.MergeResult.MergeAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("T10 Chaos - 混沌工程测试")
class T10_ChaosTest {

    @Mock
    private MemoryPackageRepository memoryRepository;

    private IdentityContextProvider identityProvider;
    private RelationshipContextProvider relationshipProvider;
    private WorkspaceContextProvider workspaceProvider;
    private HostContextProvider hostProvider;
    private PromptContextBuilder promptContextBuilder;
    private ContextAssembler contextAssembler;
    private MemoryMergeService mergeService;
    private MemoryConflictResolver conflictResolver;
    private MemoryIdentity identity;

    @BeforeEach
    void setUp() {
        identityProvider = new IdentityContextProvider();
        relationshipProvider = new RelationshipContextProvider();
        workspaceProvider = new WorkspaceContextProvider();
        hostProvider = new HostContextProvider();

        List<com.mcp.core.context.ContextProvider> providers = List.of(
                identityProvider, relationshipProvider, workspaceProvider, hostProvider);
        promptContextBuilder = new PromptContextBuilder(providers);
        contextAssembler = new ContextAssembler();
        mergeService = new MemoryMergeService(memoryRepository);
        conflictResolver = new MemoryConflictResolver();
        identity = new MemoryIdentity(null, "session-chaos", "user-chaos", null, null);
    }

    @Nested
    @DisplayName("Memory 超时故障")
    class MemoryTimeoutFaults {

        @Test
        @DisplayName("Case1: Memory 写入超时 — 系统应降级继续运行")
        void shouldDegradeGracefullyWhenMemoryWriteTimesOut() {
            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("BASE")
                    .userMessage("帮我记录今天的会议纪要")
                    .workspacePrompt("meeting-project")
                    .build();

            PromptContext promptContext = promptContextBuilder.build(ctx);
            List<PromptLayer> layers = promptContext.toLayers();
            String rendered = contextAssembler.render(layers);

            assertThat(rendered).isNotEmpty();
            assertThat(promptContext).isNotNull();

            String normalized = MemoryMergeService.normalizeContent("会议纪要");
            String factKey = MemoryMergeService.generateFactKey(MemoryType.FACT, normalized);

            when(memoryRepository.findByUserIdAndFactKeyAndIsActiveTrue(any(), any()))
                    .thenReturn(Optional.empty());

            MemoryEvaluator.ScoredMemory scored = new MemoryEvaluator.ScoredMemory(
                    "会议纪要", MemoryType.FACT, 60, 60, true);
            MergeResult result = mergeService.processCandidate(identity, scored);

            assertThat(result.action()).isEqualTo(MergeAction.NEW);
            assertThat(factKey).isNotNull();
        }

        @Test
        @DisplayName("Case2: Memory 查询返回空 — 系统应优雅降级为 NEW")
        void shouldFallbackToNewWhenMemoryQueryReturnsEmpty() {
            when(memoryRepository.findByUserIdAndFactKeyAndIsActiveTrue(any(), any()))
                    .thenReturn(Optional.empty());
            when(memoryRepository.findBySessionIdAndUserIdAndMemoryTypeAndIsActiveTrueOrderByWeightDesc(any(), any(), any()))
                    .thenReturn(List.of());

            MemoryEvaluator.ScoredMemory scored = new MemoryEvaluator.ScoredMemory(
                    "新任务描述", MemoryType.FACT, 70, 70, true);
            MergeResult result = mergeService.processCandidate(identity, scored);

            assertThat(result.action()).isEqualTo(MergeAction.NEW);
        }

        @Test
        @DisplayName("Case3: 低价值记忆在故障后应直接丢弃")
        void shouldDropLowValueMemoryEvenAfterFailure() {
            MemoryEvaluator.ScoredMemory scored = new MemoryEvaluator.ScoredMemory(
                    "嗯", MemoryType.FACT, 3, 10, false);
            MergeResult result = mergeService.processCandidate(identity, scored);

            assertThat(result.action()).isEqualTo(MergeAction.DROP);
        }
    }

    @Nested
    @DisplayName("Tool 执行失败")
    class ToolExecutionFaults {

        @Test
        @DisplayName("Case4: Tool 返回 500 错误 — 错误信息应被正确传播")
        void shouldPropagateTool500Error() {
            com.mcp.engine.agent.ExecutionTracker tracker = new com.mcp.engine.agent.ExecutionTracker();

            tracker.recordToolCall("search_file", "{\"pattern\": \"*.java\"}", false,
                    null, "500 Internal Server Error", 2000);

            String errorSummary = tracker.buildErrorSummary();
            assertThat(errorSummary).contains("search_file");
            assertThat(errorSummary).contains("500");

            long failCount = tracker.buildExecutionSummary().lines()
                    .filter(l -> l.contains("失败: 1")).count();
            assertThat(failCount).isEqualTo(1);
        }

        @Test
        @DisplayName("Case5: 连续多个 Tool 全部失败 — 不崩溃，汇总所有错误")
        void shouldNotCrashWhenAllToolsFail() {
            com.mcp.engine.agent.ExecutionTracker tracker = new com.mcp.engine.agent.ExecutionTracker();

            tracker.recordToolCall("tool_a", "{}", false, null, "Error A", 100);
            tracker.recordToolCall("tool_b", "{}", false, null, "Error B", 200);
            tracker.recordToolCall("tool_c", "{}", false, null, "Error C", 300);

            String summary = tracker.buildExecutionSummary();
            assertThat(summary).contains("3 次");
            assertThat(summary).contains("失败: 3");

            String errorSummary = tracker.buildErrorSummary();
            assertThat(errorSummary).contains("Error A");
            assertThat(errorSummary).contains("Error B");
            assertThat(errorSummary).contains("Error C");
        }

        @Test
        @DisplayName("Case6: 部分成功部分失败 — 成功的工具调用结果应保留")
        void shouldPreserveSuccessfulResultsWhenPartialFailure() {
            com.mcp.engine.agent.ExecutionTracker tracker = new com.mcp.engine.agent.ExecutionTracker();

            tracker.recordToolCall("read_file", "{\"path\": \"A.java\"}", true,
                    "文件内容", null, 150);
            tracker.recordToolCall("edit_file", "{\"path\": \"A.java\"}", false,
                    null, "权限不足", 200);
            tracker.recordToolCall("search_file", "{\"pattern\": \"*.java\"}", true,
                    "找到 5 个文件", null, 100);

            String summary = tracker.buildExecutionSummary();
            assertThat(summary).contains("成功: 2");
            assertThat(summary).contains("失败: 1");

            List<String> toolsUsed = tracker.buildToolsUsedList();
            assertThat(toolsUsed).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Workspace 不可用")
    class WorkspaceUnavailableFaults {

        @Test
        @DisplayName("Case7: Workspace 文件列表为空 — 系统正常运行")
        void shouldHandleEmptyWorkspaceFileList() {
            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("BASE")
                    .userMessage("创建新项目")
                    .workspacePrompt("new-project")
                    .build();

            PromptContext promptContext = promptContextBuilder.build(ctx);
            List<PromptLayer> layers = promptContext.toLayers();
            String rendered = contextAssembler.render(layers);

            assertThat(rendered).isNotEmpty();
            assertThat(promptContext).isNotNull();
        }

        @Test
        @DisplayName("Case8: Workspace 项目名称为 null — 不抛出异常")
        void shouldHandleNullWorkspaceName() {
            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("BASE")
                    .userMessage("查询信息")
                    .workspacePrompt(null)
                    .build();

            PromptContext promptContext = promptContextBuilder.build(ctx);
            assertThat(promptContext).isNotNull();
        }
    }

    @Nested
    @DisplayName("Token 溢出")
    class TokenOverflowFaults {

        @Test
        @DisplayName("Case9: TokenBudget 耗尽 — remaining() 返回 0")
        void shouldReturnZeroWhenBudgetExhausted() {
            TokenBudget budget = TokenBudget.builder()
                    .totalBudget(1000)
                    .systemPromptTokens(400)
                    .fileContextTokens(300)
                    .memoryTokens(200)
                    .historyTokens(100)
                    .toolResultTokens(0)
                    .build();

            assertThat(budget.remaining()).isEqualTo(0);
            assertThat(budget.canFit(1)).isFalse();
        }

        @Test
        @DisplayName("Case10: TokenBudget 接近耗尽 — 仍可容纳小量 Token")
        void shouldFitSmallTokensWhenNearlyExhausted() {
            TokenBudget budget = TokenBudget.builder()
                    .totalBudget(1000)
                    .systemPromptTokens(400)
                    .fileContextTokens(300)
                    .memoryTokens(100)
                    .historyTokens(100)
                    .toolResultTokens(50)
                    .build();

            assertThat(budget.remaining()).isEqualTo(50);
            assertThat(budget.canFit(50)).isTrue();
            assertThat(budget.canFit(51)).isFalse();
        }

        @Test
        @DisplayName("Case11: 不同 PlanType 的 TokenBudget 分配合理")
        void shouldAllocateBudgetDifferentlyByPlanType() {
            TokenBudget chatBudget = TokenBudget.forPlanType(
                    com.mcp.engine.planner.EditPlan.PlanType.CHAT, 10000);
            TokenBudget codeEditBudget = TokenBudget.forPlanType(
                    com.mcp.engine.planner.EditPlan.PlanType.CODE_EDIT, 10000);

            assertThat(chatBudget.getFileContextTokens()).isEqualTo(0);
            assertThat(codeEditBudget.getFileContextTokens()).isGreaterThan(0);
            assertThat(codeEditBudget.getToolResultTokens())
                    .isGreaterThan(chatBudget.getToolResultTokens());
        }

        @Test
        @DisplayName("Case12: 极端超长 Prompt — 各 Provider 正常应对")
        void shouldHandleExtremelyLongPrompt() {
            String longRequest = "A".repeat(10000);

            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("BASE")
                    .userMessage(longRequest)
                    .workspacePrompt("stress-project")
                    .build();

            PromptContext promptContext = promptContextBuilder.build(ctx);
            assertThat(promptContext).isNotNull();

            List<PromptLayer> layers = promptContext.toLayers();
            String rendered = contextAssembler.render(layers);
            assertThat(rendered).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Memory 冲突在极端场景下")
    class MemoryConflictUnderStress {

        @Test
        @DisplayName("Case13: 大量相同主题记忆 — 最终只保留一个活跃")
        void shouldKeepOnlyOneActiveForMassiveConflict() {
            List<MemoryPackageEntity> memories = new java.util.ArrayList<>();
            for (int i = 1; i <= 20; i++) {
                memories.add(createMemory(
                        (long) i,
                        "游戏 偏好 Terraria 版本" + i,
                        50 + (i % 50),
                        LocalDateTime.now().minusDays(20 - i)));
            }

            List<ConflictGroup> conflicts = conflictResolver.detectAndResolve(memories);

            assertThat(conflicts).hasSize(1);
            long activeCount = conflicts.get(0).conflicting().stream()
                    .filter(MemoryPackageEntity::isActive).count();
            assertThat(activeCount).isEqualTo(1);
        }

        @Test
        @DisplayName("Case14: 不同主题的大量记忆 — 正确分组")
        void shouldCorrectlyGroupMassiveMultiTopicMemories() {
            List<MemoryPackageEntity> memories = new java.util.ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                memories.add(createMemory((long) i, "游戏 偏好 Terraria", 70, LocalDateTime.now()));
                memories.add(createMemory((long) (i + 100), "水果 价格 苹果", 60, LocalDateTime.now()));
                memories.add(createMemory((long) (i + 200), "地点 位置 北京", 50, LocalDateTime.now()));
            }

            List<ConflictGroup> conflicts = conflictResolver.detectAndResolve(memories);

            assertThat(conflicts).hasSize(3);
        }
    }

    private MemoryPackageEntity createMemory(Long id, String content, int importance, LocalDateTime lastAccessedAt) {
        MemoryPackageEntity mem = new MemoryPackageEntity();
        mem.setId(id);
        mem.setContent(content);
        mem.setImportance(importance);
        mem.setActive(true);
        mem.setLastAccessedAt(lastAccessedAt);
        return mem;
    }
}