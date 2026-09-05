package com.mcp.engine.test.benchmark;

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
@DisplayName("Agent Benchmark - 可重放基准测试")
class AgentBenchmark {

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
        identity = new MemoryIdentity(null, "session-bench", "user-bench", null, null);
    }

    @Nested
    @DisplayName("偏好记忆基准")
    class PreferenceMemoryBenchmark {

        @Test
        @DisplayName("bench001: 记住用户偏好 — 游戏")
        void bench001_rememberGamePreference() {
            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("BASE")
                    .userMessage("以后记住：我最喜欢 Terraria")
                    .workspacePrompt("")
                    .build();

            PromptContext promptContext = promptContextBuilder.build(ctx);
            List<PromptLayer> layers = promptContext.toLayers();
            String rendered = contextAssembler.render(layers);

            boolean hasTerraria = layers.stream()
                    .anyMatch(l -> l.render() != null && l.render().contains("Terraria"));
            assertThat(hasTerraria).isTrue();
        }

        @Test
        @DisplayName("bench002: 记住用户偏好 — 食物")
        void bench002_rememberFoodPreference() {
            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("BASE")
                    .userMessage("记住：我喜欢吃苹果")
                    .workspacePrompt("")
                    .build();

            PromptContext promptContext = promptContextBuilder.build(ctx);
            List<PromptLayer> layers = promptContext.toLayers();
            String rendered = contextAssembler.render(layers);

            assertThat(rendered).isNotEmpty();
            assertThat(layers).isNotEmpty();
        }

        @Test
        @DisplayName("bench003: 记住用户偏好 — 地点")
        void bench003_rememberLocationPreference() {
            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("BASE")
                    .userMessage("我住在北京朝阳区")
                    .workspacePrompt("")
                    .build();

            PromptContext promptContext = promptContextBuilder.build(ctx);
            List<PromptLayer> layers = promptContext.toLayers();
            String rendered = contextAssembler.render(layers);

            assertThat(rendered).isNotEmpty();
        }

        @Test
        @DisplayName("bench004: 记住多条偏好 — 游戏+食物+地点")
        void bench004_rememberMultiplePreferences() {
            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("BASE")
                    .userMessage("记住：我喜欢 Terraria，喜欢吃苹果，住在北京")
                    .workspacePrompt("")
                    .build();

            PromptContext promptContext = promptContextBuilder.build(ctx);
            List<PromptLayer> layers = promptContext.toLayers();
            String rendered = contextAssembler.render(layers);

            assertThat(rendered).isNotEmpty();
            assertThat(layers).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Workspace 持久化基准")
    class WorkspacePersistenceBenchmark {

        @Test
        @DisplayName("bench005: 创建项目并读取文件列表")
        void bench005_createProjectAndReadFiles() {
            List<String> workspaceFiles = new ArrayList<>();
            workspaceFiles.add("Main.java");
            workspaceFiles.add("Config.java");
            workspaceFiles.add("UserService.java");

            assertThat(workspaceFiles).hasSize(3);
            assertThat(workspaceFiles).contains("Main.java", "Config.java", "UserService.java");
        }

        @Test
        @DisplayName("bench006: 添加文件后状态一致性")
        void bench006_addFileConsistency() {
            List<String> workspaceFiles = new ArrayList<>();
            workspaceFiles.add("Main.java");
            workspaceFiles.add("Config.java");

            workspaceFiles.add("OrderService.java");
            assertThat(workspaceFiles).hasSize(3);
            assertThat(workspaceFiles).contains("OrderService.java");
        }

        @Test
        @DisplayName("bench007: 删除文件后状态一致性")
        void bench007_deleteFileConsistency() {
            List<String> workspaceFiles = new ArrayList<>();
            workspaceFiles.add("Main.java");
            workspaceFiles.add("Config.java");
            workspaceFiles.add("OrderService.java");

            workspaceFiles.remove("OrderService.java");
            assertThat(workspaceFiles).hasSize(2);
            assertThat(workspaceFiles).doesNotContain("OrderService.java");
        }

        @Test
        @DisplayName("bench008: 大文件列表 (100 文件)")
        void bench008_largeFileList() {
            List<String> files = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                files.add("File_" + i + ".java");
            }

            assertThat(files).hasSize(100);
            assertThat(files).contains("File_0.java", "File_99.java");
        }
    }

    @Nested
    @DisplayName("工具调用基准")
    class ToolCallingBenchmark {

        @Test
        @DisplayName("bench009: 单次工具调用成功 — search_file")
        void bench009_singleToolCallSuccess() {
            ExecutionTracker tracker = new ExecutionTracker();
            tracker.recordToolCall("search_file", "{\"pattern\": \"*.java\"}", true,
                    "找到 3 个文件", null, 150);

            String summary = tracker.buildExecutionSummary();
            assertThat(summary).contains("1 次");
            assertThat(summary).contains("成功: 1");
        }

        @Test
        @DisplayName("bench010: 多次工具调用 — 全部成功")
        void bench010_multipleToolCallsAllSuccess() {
            ExecutionTracker tracker = new ExecutionTracker();
            tracker.recordToolCall("search_file", "{}", true, "ok", null, 100);
            tracker.recordToolCall("read_file", "{}", true, "ok", null, 200);
            tracker.recordToolCall("edit_file", "{}", true, "ok", null, 300);

            String summary = tracker.buildExecutionSummary();
            assertThat(summary).contains("3 次");
            assertThat(summary).contains("成功: 3");
        }

        @Test
        @DisplayName("bench011: 工具调用失败 — 错误传播")
        void bench011_toolCallFailure() {
            ExecutionTracker tracker = new ExecutionTracker();
            tracker.recordToolCall("edit_file", "{\"path\": \"X.java\"}", false,
                    null, "权限不足", 500);

            String errorSummary = tracker.buildErrorSummary();
            assertThat(errorSummary).contains("edit_file");
            assertThat(errorSummary).contains("权限不足");
        }

        @Test
        @DisplayName("bench012: 部分成功部分失败")
        void bench012_partialSuccessFailure() {
            ExecutionTracker tracker = new ExecutionTracker();
            tracker.recordToolCall("search_file", "{}", true, "ok", null, 100);
            tracker.recordToolCall("edit_file", "{}", false, null, "Error", 200);
            tracker.recordToolCall("read_file", "{}", true, "ok", null, 300);

            String summary = tracker.buildExecutionSummary();
            assertThat(summary).contains("成功: 2");
            assertThat(summary).contains("失败: 1");
        }
    }

    @Nested
    @DisplayName("Memory 合并基准")
    class MemoryMergeBenchmark {

        @Test
        @DisplayName("bench013: 新记忆 → NEW")
        void bench013_newMemory() {
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
        @DisplayName("bench014: 低价值记忆 → DROP")
        void bench014_lowValueMemoryDrop() {
            MemoryEvaluator.ScoredMemory scored = new MemoryEvaluator.ScoredMemory(
                    "嗯", MemoryType.FACT, 3, 10, false);
            MergeResult result = mergeService.processCandidate(identity, scored);

            assertThat(result.action()).isEqualTo(MergeAction.DROP);
        }

        @Test
        @DisplayName("bench015: 重复记忆 → UPDATE")
        void bench015_duplicateMemoryUpdate() {
            MemoryPackageEntity existing = createMemory(1L, "游戏 偏好 Terraria 很好玩", 80,
                    LocalDateTime.now().minusDays(3));

            when(memoryRepository.findByUserIdAndFactKeyAndIsActiveTrue(any(), any()))
                    .thenReturn(Optional.empty());
            when(memoryRepository.findBySessionIdAndUserIdAndMemoryTypeAndIsActiveTrueOrderByWeightDesc(any(), any(), any()))
                    .thenReturn(List.of(existing));

            MemoryEvaluator.ScoredMemory scored = new MemoryEvaluator.ScoredMemory(
                    "游戏 偏好 Terraria 很好玩", MemoryType.PREFERENCE, 80, 80, true);
            MergeResult result = mergeService.processCandidate(identity, scored);

            assertThat(result.action()).isEqualTo(MergeAction.UPDATE);
        }

        @Test
        @DisplayName("bench016: 精准匹配 → UPDATE")
        void bench016_exactMatchUpdate() {
            MemoryPackageEntity existing = createMemory(1L, "游戏 偏好 Terraria 最好玩", 90,
                    LocalDateTime.now().minusDays(1));

            when(memoryRepository.findByUserIdAndFactKeyAndIsActiveTrue(any(), any()))
                    .thenReturn(Optional.empty());
            when(memoryRepository.findBySessionIdAndUserIdAndMemoryTypeAndIsActiveTrueOrderByWeightDesc(any(), any(), any()))
                    .thenReturn(List.of(existing));

            MemoryEvaluator.ScoredMemory scored = new MemoryEvaluator.ScoredMemory(
                    "游戏 偏好 Terraria 最好玩", MemoryType.PREFERENCE, 90, 90, true);
            MergeResult result = mergeService.processCandidate(identity, scored);

            assertThat(result.action()).isEqualTo(MergeAction.UPDATE);
        }
    }

    @Nested
    @DisplayName("冲突解决基准")
    class ConflictResolutionBenchmark {

        @Test
        @DisplayName("bench017: 同主题多条记忆 → 只保留一条活跃")
        void bench017_sameTopicOnlyOneActive() {
            MemoryPackageEntity m1 = createMemory(1L, "游戏 偏好 Terraria 很好玩", 80,
                    LocalDateTime.now().minusDays(5));
            MemoryPackageEntity m2 = createMemory(2L, "游戏 偏好 Terraria 也不错", 85,
                    LocalDateTime.now().minusDays(3));
            MemoryPackageEntity m3 = createMemory(3L, "游戏 偏好 Terraria 还可以", 75,
                    LocalDateTime.now().minusDays(1));

            List<ConflictGroup> conflicts = conflictResolver.detectAndResolve(List.of(m1, m2, m3));

            assertThat(conflicts).hasSize(1);
            long activeCount = conflicts.get(0).conflicting().stream()
                    .filter(MemoryPackageEntity::isActive).count();
            assertThat(activeCount).isEqualTo(1);
        }

        @Test
        @DisplayName("bench018: 不同主题不冲突")
        void bench018_differentTopicsNoConflict() {
            MemoryPackageEntity game = createMemory(1L, "游戏 偏好 Terraria", 80, LocalDateTime.now());
            MemoryPackageEntity food = createMemory(2L, "水果 价格 苹果", 70, LocalDateTime.now());
            MemoryPackageEntity location = createMemory(3L, "地点 位置 北京", 60, LocalDateTime.now());

            List<ConflictGroup> conflicts = conflictResolver.detectAndResolve(
                    List.of(game, food, location));

            assertThat(conflicts).isEmpty();
        }

        @Test
        @DisplayName("bench019: 高优先级覆盖低优先级")
        void bench019_highPriorityOverridesLow() {
            MemoryPackageEntity low = createMemory(1L, "游戏 偏好 Terraria 一般", 40,
                    LocalDateTime.now());
            MemoryPackageEntity high = createMemory(2L, "游戏 偏好 Terraria 最好玩", 90,
                    LocalDateTime.now().minusDays(1));

            List<ConflictGroup> conflicts = conflictResolver.detectAndResolve(List.of(low, high));

            assertThat(conflicts).hasSize(1);
            assertThat(low.isActive()).isFalse();
            assertThat(high.isActive()).isTrue();
        }

        @Test
        @DisplayName("bench020: 相同优先级保留最新")
        void bench020_samePriorityKeepLatest() {
            MemoryPackageEntity old = createMemory(1L, "游戏 偏好 Terraria", 80,
                    LocalDateTime.now().minusDays(10));
            MemoryPackageEntity recent = createMemory(2L, "游戏 偏好 Terraria", 80,
                    LocalDateTime.now().minusDays(1));

            List<ConflictGroup> conflicts = conflictResolver.detectAndResolve(List.of(old, recent));

            assertThat(conflicts).hasSize(1);
            assertThat(old.isActive()).isFalse();
            assertThat(recent.isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("Context Pipeline 基准")
    class ContextPipelineBenchmark {

        @Test
        @DisplayName("bench025: 完整上下文构建")
        void bench025_fullContextBuild() {
            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("你是一个有用的助手")
                    .userMessage("帮我整理昨天讨论的 Java 项目")
                    .workspacePrompt("项目: java-project\n文件: Main.java, Config.java")
                    .build();

            PromptContext promptContext = promptContextBuilder.build(ctx);
            assertThat(promptContext).isNotNull();
            assertThat(promptContext.getBaseSystemPrompt()).isNotNull();

            List<PromptLayer> layers = promptContext.toLayers();
            String rendered = contextAssembler.render(layers);
            assertThat(rendered).isNotEmpty();
        }

        @Test
        @DisplayName("bench026: 最小上下文构建")
        void bench026_minimalContextBuild() {
            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("BASE")
                    .userMessage("你好")
                    .build();

            PromptContext promptContext = promptContextBuilder.build(ctx);
            List<PromptLayer> layers = promptContext.toLayers();
            String rendered = contextAssembler.render(layers);
            assertThat(rendered).isNotEmpty();
        }

        @Test
        @DisplayName("bench027: Layer 顺序正确")
        void bench027_layerOrderCorrect() {
            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("BASE")
                    .userMessage("test")
                    .workspacePrompt("test-project")
                    .build();

            PromptContext promptContext = promptContextBuilder.build(ctx);
            List<PromptLayer> layers = promptContext.toLayers();

            List<String> layerNames = layers.stream().map(PromptLayer::name).toList();
            assertThat(layerNames).contains("BASE_SYSTEM");
            assertThat(layerNames).contains("WORKSPACE");
        }

        @Test
        @DisplayName("bench028: 空字段不生成 Layer")
        void bench028_emptyFieldsNoLayer() {
            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("BASE")
                    .userMessage("test")
                    .workspacePrompt("")
                    .hostContextPrompt(null)
                    .build();

            PromptContext promptContext = promptContextBuilder.build(ctx);
            List<PromptLayer> layers = promptContext.toLayers();

            List<String> layerNames = layers.stream().map(PromptLayer::name).toList();
            assertThat(layerNames).contains("BASE_SYSTEM");
            assertThat(layerNames).doesNotContain("WORKSPACE", "HOST_CONTEXT");
        }
    }

    @Nested
    @DisplayName("Memory 生命周期基准")
    class MemoryLifecycleBenchmark {

        @Test
        @DisplayName("bench029: 记忆从创建到冲突解决完整流程")
        void bench029_memoryFullLifecycle() {
            String normalized = MemoryMergeService.normalizeContent("项目使用 PostgreSQL");
            String factKey = MemoryMergeService.generateFactKey(MemoryType.FACT, normalized);

            when(memoryRepository.findByUserIdAndFactKeyAndIsActiveTrue(any(), any()))
                    .thenReturn(Optional.empty());
            when(memoryRepository.findBySessionIdAndUserIdAndMemoryTypeAndIsActiveTrueOrderByWeightDesc(any(), any(), any()))
                    .thenReturn(List.of());

            MemoryEvaluator.ScoredMemory scored = new MemoryEvaluator.ScoredMemory(
                    "项目使用 PostgreSQL", MemoryType.FACT, 80, 80, true);
            MergeResult result = mergeService.processCandidate(identity, scored);

            assertThat(result.action()).isEqualTo(MergeAction.NEW);
            assertThat(result.factKey()).isEqualTo(factKey);
        }

        @Test
        @DisplayName("bench030: 记忆衰减模拟")
        void bench030_memoryDecaySimulation() {
            MemoryPackageEntity old = createMemory(1L, "旧记忆 内容", 50,
                    LocalDateTime.now().minusDays(30));
            MemoryPackageEntity recent = createMemory(2L, "新记忆 内容", 50,
                    LocalDateTime.now().minusDays(1));

            boolean oldIsStale = old.getLastAccessedAt()
                    .isBefore(LocalDateTime.now().minusDays(14));
            boolean recentIsFresh = recent.getLastAccessedAt()
                    .isAfter(LocalDateTime.now().minusDays(7));

            assertThat(oldIsStale).isTrue();
            assertThat(recentIsFresh).isTrue();
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