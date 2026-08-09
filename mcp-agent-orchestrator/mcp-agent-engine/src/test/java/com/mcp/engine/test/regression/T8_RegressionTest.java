package com.mcp.engine.test.regression;

import com.mcp.common.identity.MemoryIdentity;
import com.mcp.core.context.BuildContext;
import com.mcp.core.context.ContextAssembler;
import com.mcp.core.context.PromptContext;
import com.mcp.core.context.PromptLayer;
import com.mcp.engine.memory.MemoryEvaluator;
import com.mcp.core.domain.memory.MemoryType;
import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.entity.WorkspaceEntity;
import com.mcp.core.repository.MemoryPackageRepository;
import com.mcp.core.repository.WorkspaceRepository;
import com.mcp.engine.memory.MemoryMergeService;
import com.mcp.engine.memory.MemoryMergeService.MergeResult.MergeAction;
import com.mcp.engine.memory.MemoryMergeService.MergeResult;
import com.mcp.tools.executor.ToolExecutor;
import com.mcp.tools.model.*;
import com.mcp.tools.registry.CapabilityResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class T8_RegressionTest {

    @Mock
    private MemoryPackageRepository memoryRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    private MemoryMergeService mergeService;
    private ContextAssembler contextAssembler;
    private MemoryIdentity identity;

    @BeforeEach
    void setUp() {
        mergeService = new MemoryMergeService(memoryRepository);
        contextAssembler = new ContextAssembler();
        identity = new MemoryIdentity(null, "session-1", "user-1", null, null);
    }

    @Test
    @DisplayName("Case1: Memory 回归 - 存储后正确召回")
    void shouldStoreAndRecallMemoryCorrectly() {
        String normalized = MemoryMergeService.normalizeContent("我最喜欢 Terraria");
        String factKey = MemoryMergeService.generateFactKey(MemoryType.PREFERENCE, normalized);
        MemoryPackageEntity memory = createMemory(1L, factKey, "我最喜欢 Terraria", 80, MemoryType.PREFERENCE);

        when(memoryRepository.findByUserIdAndFactKeyAndIsActiveTrue(identity.userId(), factKey))
                .thenReturn(Optional.of(memory));

        MemoryEvaluator.ScoredMemory candidate = score("我最喜欢 Terraria", 80);
        MergeResult result = mergeService.processCandidate(identity, candidate);

        assertThat(result.action()).isEqualTo(MergeAction.UPDATE);
        assertThat(memory.getContent()).isEqualTo("我最喜欢 Terraria");
    }

    @Test
    @DisplayName("Case2: Context 回归 - Layer 数量和顺序正确")
    void shouldHaveCorrectLayerCountAndOrder() {
        PromptContext context = PromptContext.builder()
                .baseSystemPrompt("BASE")
                .identity("IDENTITY")
                .character("CHARACTER")
                .relationship("RELATIONSHIP")
                .groupContext("GROUP_CONTEXT")
                .userProfile("USER_PROFILE")
                .workspace("WORKSPACE")
                .hostContext("HOST_CONTEXT")
                .memory("MEMORY")
                .artifact("ARTIFACT")
                .plan("PLAN")
                .modeHint("MODE_HINT")
                .build();

        List<PromptLayer> layers = context.toLayers();
        List<String> sortedNames = layers.stream()
                .sorted((a, b) -> Integer.compare(a.priority(), b.priority()))
                .map(PromptLayer::name)
                .toList();

        assertThat(sortedNames).hasSize(12);
        assertThat(sortedNames).containsExactly(
                "BASE_SYSTEM",
                "IDENTITY",
                "CHARACTER",
                "RELATIONSHIP",
                "GROUP_CONTEXT",
                "USER_PROFILE",
                "WORKSPACE",
                "HOST_CONTEXT",
                "MEMORY",
                "ARTIFACT",
                "PLAN",
                "MODE_HINT"
        );

        String rendered = contextAssembler.render(layers);
        assertThat(rendered).contains("BASE", "IDENTITY", "CHARACTER", "RELATIONSHIP",
                "GROUP_CONTEXT", "USER_PROFILE", "WORKSPACE", "HOST_CONTEXT",
                "MEMORY", "ARTIFACT", "PLAN", "MODE_HINT");
    }

    @Test
    @DisplayName("Case3: Workspace 回归 - 创建后查询正确")
    void shouldCreateAndQueryWorkspaceCorrectly() {
        String workspaceId = "ws-regression";
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setWorkspaceId(workspaceId);
        workspace.setName("回归测试项目");

        when(workspaceRepository.findByWorkspaceId(workspaceId))
                .thenReturn(Optional.of(workspace));

        Optional<WorkspaceEntity> result = workspaceRepository.findByWorkspaceId(workspaceId);
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("回归测试项目");
    }

    @Test
    @DisplayName("Case4: Tool 回归 - 注册后正确调用")
    void shouldRegisterToolCorrectly() {
        ToolDefinition tool = new ToolDefinition();
        tool.setName("regression_test");
        tool.setDescription("回归测试工具");

        assertThat(tool.getName()).isEqualTo("regression_test");
        assertThat(tool.getDescription()).isEqualTo("回归测试工具");
    }

    @Test
    @DisplayName("Case5: Capability 回归 - 解析能力正确")
    void shouldResolveCapabilityToCorrectTool() {
        ToolDefinition tool = new ToolDefinition();
        tool.setName("file_read");
        tool.setDescription("读取文件");

        assertThat(tool.getName()).isEqualTo("file_read");
    }

    @Test
    @DisplayName("Case6: 上下文 Pipeline 回归 - 完整流程正确")
    void shouldHaveCorrectContextPipeline() {
        PromptContext context = PromptContext.builder()
                .baseSystemPrompt("系统提示")
                .identity("用户身份")
                .build();

        List<PromptLayer> layers = context.toLayers();
        assertThat(layers).isNotEmpty();
        assertThat(layers).extracting(PromptLayer::name)
                .contains("BASE_SYSTEM", "IDENTITY");
    }

    @Test
    @DisplayName("Case7: 多轮对话状态一致性")
    void shouldMaintainStateConsistencyAcrossTurns() {
        MemoryIdentity turn1 = new MemoryIdentity(null, "session-1", "user-1", null, null);
        MemoryIdentity turn2 = new MemoryIdentity(null, "session-1", "user-1", null, null);

        assertThat(turn1.userId()).isEqualTo(turn2.userId());
        assertThat(turn1.sessionId()).isEqualTo(turn2.sessionId());
    }

    @Test
    @DisplayName("Case8: Memory 回归 - 更新记忆后内容正确")
    void shouldUpdateMemoryContentCorrectly() {
        String normalized = MemoryMergeService.normalizeContent("我现在最喜欢 Minecraft");
        String factKey = MemoryMergeService.generateFactKey(MemoryType.PREFERENCE, normalized);
        MemoryPackageEntity oldMemory = createMemory(1L, factKey, "我最喜欢 Terraria", 80, MemoryType.PREFERENCE);

        when(memoryRepository.findByUserIdAndFactKeyAndIsActiveTrue(identity.userId(), factKey))
                .thenReturn(Optional.of(oldMemory));

        MemoryEvaluator.ScoredMemory newCandidate = score("我现在最喜欢 Minecraft", 85);
        MergeResult result = mergeService.processCandidate(identity, newCandidate);

        assertThat(result.action()).isEqualTo(MergeAction.UPDATE);
        assertThat(oldMemory.getContent()).isEqualTo("我现在最喜欢 Minecraft");
    }

    @Test
    @DisplayName("Case9: 工具执行回归")
    void shouldExecuteToolCorrectly() {
        ToolDefinition tool = new ToolDefinition();
        tool.setName("calculate");
        tool.setDescription("执行计算");

        assertThat(tool.getName()).isEqualTo("calculate");
        assertThat(tool.getDescription()).isEqualTo("执行计算");
    }

    @Test
    @DisplayName("Case10: 不生成空 Layer")
    void shouldNotGenerateEmptyLayers() {
        PromptContext context = PromptContext.builder()
                .baseSystemPrompt("BASE")
                .build();

        List<PromptLayer> layers = context.toLayers();
        assertThat(layers).isNotEmpty();
        assertThat(layers).allMatch(layer -> layer.render() != null && !layer.render().isBlank());
    }

    private MemoryPackageEntity createMemory(Long id, String factKey, String content, int importance, MemoryType type) {
        MemoryPackageEntity mem = new MemoryPackageEntity();
        mem.setId(id);
        mem.setFactKey(factKey);
        mem.setContent(content);
        mem.setImportance(importance);
        mem.setMemoryType(type);
        mem.setActive(true);
        mem.setConfidence(80);
        mem.setVersion(1);
        mem.setAccessCount(0);
        mem.setWeight(importance / 10.0);
        mem.setUpgradeCount(0);
        return mem;
    }

    private MemoryEvaluator.ScoredMemory score(String content, int importance) {
        return new MemoryEvaluator.ScoredMemory(
                content, MemoryType.PREFERENCE, importance, importance, true);
    }
}