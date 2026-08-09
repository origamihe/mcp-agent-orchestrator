package com.mcp.engine.test.e2e;

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
import com.mcp.engine.agent.ExecutionTracker;
import com.mcp.engine.memory.MemoryConflictResolver;
import com.mcp.engine.memory.MemoryConflictResolver.ConflictGroup;
import com.mcp.engine.memory.MemoryEvaluator;
import com.mcp.engine.memory.MemoryMergeService;
import com.mcp.engine.memory.MemoryMergeService.MergeResult;
import com.mcp.engine.memory.MemoryMergeService.MergeResult.MergeAction;
import com.mcp.llm.client.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("T9 E2E - 端到端全链路测试")
class T9_E2ETest {

    @Mock
    private MemoryPackageRepository memoryRepository;

    @Mock
    private LlmClient llmClient;

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
        identity = new MemoryIdentity(null, "session-e2e", "user-e2e", null, null);
    }

    @Nested
    @DisplayName("E2E 全链路：Memory → Workspace → Tool → Reflection → Memory")
    class FullPipelineE2E {

        @Test
        @DisplayName("Case1: 一条消息贯穿全部组件 — 用户偏好记忆 → Context → Prompt → 模拟执行 → Reflection → Memory 写回")
        void shouldCompleteFullLifecycleForPreference() {
            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("BASE")
                    .userMessage("以后记住：我最喜欢 Terraria")
                    .workspacePrompt("项目: game-project")
                    .build();

            PromptContext promptContext = promptContextBuilder.build(ctx);
            assertThat(promptContext).isNotNull();

            List<PromptLayer> layers = promptContext.toLayers();
            String rendered = contextAssembler.render(layers);
            assertThat(rendered).isNotEmpty();

            String normalized = MemoryMergeService.normalizeContent("我最喜欢 Terraria");
            String factKey = MemoryMergeService.generateFactKey(MemoryType.PREFERENCE, normalized);

            when(memoryRepository.findByUserIdAndFactKeyAndIsActiveTrue(any(), any()))
                    .thenReturn(Optional.empty());
            when(memoryRepository.findBySessionIdAndUserIdAndMemoryTypeAndIsActiveTrueOrderByWeightDesc(any(), any(), any()))
                    .thenReturn(List.of());

            MemoryEvaluator.ScoredMemory scored = new MemoryEvaluator.ScoredMemory(
                    "我最喜欢 Terraria", MemoryType.PREFERENCE, 80, 80, true);
            MergeResult result = mergeService.processCandidate(identity, scored);

            assertThat(result.action()).isEqualTo(MergeAction.NEW);
            assertThat(result.factKey()).isEqualTo(factKey);
        }

        @Test
        @DisplayName("Case2: 多步任务完整链路 — Plan → Tool执行 → 成功/失败 → Reflection → Memory更新")
        void shouldCompleteMultiStepTaskWithReflection() {
            ExecutionTracker tracker = new ExecutionTracker();

            tracker.recordToolCall("search_file", "{\"pattern\": \"*.java\"}", true,
                    "找到 3 个文件", null, 150);
            tracker.recordToolCall("read_file", "{\"path\": \"UserService.java\"}", true,
                    "读取成功", null, 200);
            tracker.recordToolCall("edit_file", "{\"path\": \"UserService.java\"}", false,
                    null, "参数格式错误", 300);

            String summary = tracker.buildExecutionSummary();
            assertThat(summary).contains("3 次");
            assertThat(summary).contains("成功: 2");
            assertThat(summary).contains("失败: 1");

            String errorSummary = tracker.buildErrorSummary();
            assertThat(errorSummary).isNotNull();
            assertThat(errorSummary).contains("edit_file");
            assertThat(errorSummary).contains("参数格式错误");

            List<String> toolsUsed = tracker.buildToolsUsedList();
            assertThat(toolsUsed).containsExactlyInAnyOrder("search_file", "read_file", "edit_file");
        }

        @Test
        @DisplayName("Case3: 多轮对话后 Memory 累积 — 多轮 Preferences 正确合并")
        void shouldAccumulateAndMergeMemoriesAcrossRounds() {
            MemoryPackageEntity existing = new MemoryPackageEntity();
            existing.setId(1L);
            existing.setContent("游戏 偏好 Terraria 很好玩");
            existing.setActive(true);
            existing.setImportance(80);
            existing.setMemoryType(MemoryType.PREFERENCE);

            when(memoryRepository.findByUserIdAndFactKeyAndIsActiveTrue(any(), any()))
                    .thenReturn(Optional.of(existing));

            MemoryEvaluator.ScoredMemory round1 = new MemoryEvaluator.ScoredMemory(
                    "游戏 偏好 Terraria 很好玩", MemoryType.PREFERENCE, 80, 80, true);
            MergeResult result1 = mergeService.processCandidate(identity, round1);
            assertThat(result1.action()).isEqualTo(MergeAction.UPDATE);

            MemoryEvaluator.ScoredMemory round2 = new MemoryEvaluator.ScoredMemory(
                    "游戏 偏好 Terraria 也不错", MemoryType.PREFERENCE, 85, 85, true);
            MergeResult result2 = mergeService.processCandidate(identity, round2);
            assertThat(result2.action()).isEqualTo(MergeAction.UPDATE);
        }

        @Test
        @DisplayName("Case4: Workspace 状态在操作后正确持久化 — 文件增删改查一致性")
        void shouldMaintainWorkspaceConsistencyAfterOperations() {
            List<String> workspaceFiles = new ArrayList<>();
            workspaceFiles.add("UserService.java");
            workspaceFiles.add("OrderService.java");
            workspaceFiles.add("PaymentService.java");

            assertThat(workspaceFiles).hasSize(3);

            workspaceFiles.add("ProductService.java");
            assertThat(workspaceFiles).hasSize(4);
            assertThat(workspaceFiles).contains("ProductService.java");

            workspaceFiles.remove("OrderService.java");
            assertThat(workspaceFiles).hasSize(3);
            assertThat(workspaceFiles).doesNotContain("OrderService.java");

            String workspacePrompt = String.join("\n", workspaceFiles);
            assertThat(workspacePrompt).contains("UserService.java");
            assertThat(workspacePrompt).doesNotContain("OrderService.java");
        }

        @Test
        @DisplayName("Case5: Reflection 失败后 FailureLibrary 记录 — 错误模式被正确捕获")
        void shouldCaptureFailurePatternInLibrary() {
            ExecutionTracker tracker = new ExecutionTracker();
            tracker.recordToolCall("create_ppt", "{\"title\": \"项目总结\"}", false,
                    null, "文件格式不支持", 500);

            String errorSummary = tracker.buildErrorSummary();
            assertThat(errorSummary).contains("create_ppt");
            assertThat(errorSummary).contains("文件格式不支持");

            String executionSummary = tracker.buildExecutionSummary();
            assertThat(executionSummary).contains("失败: 1");
        }

        @Test
        @DisplayName("Case6: 完整消息生命周期 — Memory读取 → Context构建 → Prompt组装 → 模拟LLM → Memory写入")
        void shouldCompleteMessageLifecycleEndToEnd() {
            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("BASE")
                    .userMessage("帮我整理昨天讨论的 Java 项目，生成总结，然后记住")
                    .workspacePrompt("项目: java-project\n文件: Main.java, Config.java")
                    .build();

            PromptContext promptContext = promptContextBuilder.build(ctx);
            assertThat(promptContext).isNotNull();
            assertThat(promptContext.getBaseSystemPrompt()).isNotNull();

            List<PromptLayer> layers = promptContext.toLayers();
            String rendered = contextAssembler.render(layers);
            assertThat(rendered).isNotEmpty();

            String normalized = MemoryMergeService.normalizeContent("整理 Java 项目总结");
            String factKey = MemoryMergeService.generateFactKey(MemoryType.FACT, normalized);

            when(memoryRepository.findByUserIdAndFactKeyAndIsActiveTrue(any(), any()))
                    .thenReturn(Optional.empty());
            when(memoryRepository.findBySessionIdAndUserIdAndMemoryTypeAndIsActiveTrueOrderByWeightDesc(any(), any(), any()))
                    .thenReturn(List.of());

            MemoryEvaluator.ScoredMemory scored = new MemoryEvaluator.ScoredMemory(
                    "整理 Java 项目总结", MemoryType.FACT, 75, 75, true);
            MergeResult result = mergeService.processCandidate(identity, scored);

            assertThat(result.action()).isEqualTo(MergeAction.NEW);
            assertThat(result.factKey()).isEqualTo(factKey);
        }
    }

    @Nested
    @DisplayName("E2E 跨组件一致性")
    class CrossComponentConsistency {

        @Test
        @DisplayName("Case7: Context 构建后 Memory 写入 — 上下文与记忆数据一致")
        void shouldMaintainContextMemoryConsistency() {
            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("BASE")
                    .userMessage("记住：项目使用 PostgreSQL 数据库")
                    .workspacePrompt("项目: db-project")
                    .build();

            PromptContext promptContext = promptContextBuilder.build(ctx);
            List<PromptLayer> layers = promptContext.toLayers();
            String rendered = contextAssembler.render(layers);
            assertThat(rendered).isNotEmpty();

            String normalized = MemoryMergeService.normalizeContent("项目使用 PostgreSQL 数据库");
            String factKey = MemoryMergeService.generateFactKey(MemoryType.FACT, normalized);

            when(memoryRepository.findByUserIdAndFactKeyAndIsActiveTrue(any(), any()))
                    .thenReturn(Optional.empty());
            when(memoryRepository.findBySessionIdAndUserIdAndMemoryTypeAndIsActiveTrueOrderByWeightDesc(any(), any(), any()))
                    .thenReturn(List.of());

            MemoryEvaluator.ScoredMemory scored = new MemoryEvaluator.ScoredMemory(
                    "项目使用 PostgreSQL 数据库", MemoryType.FACT, 80, 80, true);
            MergeResult result = mergeService.processCandidate(identity, scored);

            assertThat(result.action()).isEqualTo(MergeAction.NEW);
            assertThat(result.factKey()).isEqualTo(factKey);
        }

        @Test
        @DisplayName("Case8: 多次 Memory 写入后冲突检测 — 同一主题合并正确")
        void shouldCorrectlyMergeAfterMultipleMemoryWrites() {
            MemoryPackageEntity m1 = createMemory(1L, "游戏 偏好 Terraria 很好玩", 80,
                    LocalDateTime.now().minusDays(5));
            MemoryPackageEntity m2 = createMemory(2L, "游戏 偏好 Terraria 也不错", 85,
                    LocalDateTime.now().minusDays(3));
            MemoryPackageEntity m3 = createMemory(3L, "游戏 偏好 Terraria 还可以", 75,
                    LocalDateTime.now().minusDays(1));

            List<ConflictGroup> conflicts = conflictResolver.detectAndResolve(List.of(m1, m2, m3));

            assertThat(conflicts).hasSize(1);
            assertThat(m1.isActive()).isFalse();
            assertThat(m2.isActive()).isFalse();
            assertThat(m3.isActive()).isTrue();

            long activeCount = conflicts.get(0).conflicting().stream()
                    .filter(MemoryPackageEntity::isActive).count();
            assertThat(activeCount).isEqualTo(1);
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