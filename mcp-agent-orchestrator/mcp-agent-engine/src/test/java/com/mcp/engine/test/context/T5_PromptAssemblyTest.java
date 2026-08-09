package com.mcp.engine.test.context;

import com.mcp.core.context.ContextAssembler;
import com.mcp.core.context.PromptContext;
import com.mcp.core.context.PromptLayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T5 Prompt Assembly - 验证 Layer 顺序与完整性
 *
 * 测试目标：
 * - 验证 Layer 按 priority 正确排序
 * - 验证最终顺序：BASE_SYSTEM → Identity → Relationship → Workspace → Host → Memory → Artifact → Plan → ModeHint
 * - 验证无重复 Layer
 * - 验证无遗漏 Layer（有内容的都应该渲染）
 * - 验证空 Layer 被自动跳过
 * - 验证空内容不参与渲染
 */
class T5_PromptAssemblyTest {

    private ContextAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new ContextAssembler();
    }

    @Test
    @DisplayName("Case1: 完整 PromptContext 所有层顺序正确")
    void shouldMaintainCorrectLayerOrderForFullContext() {
        PromptContext context = PromptContext.builder()
                .baseSystemPrompt("BASE: 你是一个助手")
                .identity("IDENTITY: 我是澪音")
                .character("CHARACTER: 说话友好")
                .relationship("RELATIONSHIP: 这是我的主人")
                .groupContext("GROUP_CONTEXT: 这是测试群")
                .userProfile("USER_PROFILE: 用户是 RiKo")
                .workspace("WORKSPACE: 项目 mcp-agent")
                .hostContext("HOST_CONTEXT: 当前打开 BuildContext.java")
                .memory("MEMORY: 用户喜欢 Terraria")
                .artifact("ARTIFACT: MCP 是 Model Context Protocol")
                .plan("PLAN: 1. 理解需求 2. 编写代码")
                .modeHint("MODE_HINT: 使用中文回答")
                .build();

        List<PromptLayer> layers = context.toLayers();
        String rendered = assembler.render(layers);

        List<String> layerNames = layers.stream()
                .sorted((a, b) -> Integer.compare(a.priority(), b.priority()))
                .map(PromptLayer::name)
                .toList();

        assertThat(layerNames).containsExactly(
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

        assertThat(rendered)
                .containsSubsequence("BASE: 你是一个助手", "IDENTITY: 我是澪音",
                        "RELATIONSHIP: 这是我的主人", "WORKSPACE: 项目 mcp-agent",
                        "HOST_CONTEXT: 当前打开", "MEMORY: 用户喜欢", "ARTIFACT: MCP");
    }

    @Test
    @DisplayName("Case2: 空字段不生成 Layer - 只有有内容的层参与渲染")
    void shouldOnlyIncludeLayersWithContent() {
        PromptContext context = PromptContext.builder()
                .baseSystemPrompt("BASE")
                .identity(null)
                .character("")
                .relationship(null)
                .groupContext("")
                .userProfile(null)
                .workspace("WORKSPACE")
                .memory(null)
                .build();

        List<PromptLayer> layers = context.toLayers();
        List<String> layerNames = layers.stream().map(PromptLayer::name).toList();

        assertThat(layerNames).containsExactly("BASE_SYSTEM", "WORKSPACE");
        assertThat(layerNames).doesNotContain("IDENTITY", "CHARACTER", "RELATIONSHIP",
                "GROUP_CONTEXT", "USER_PROFILE", "MEMORY");
    }

    @Test
    @DisplayName("Case3: 优先级数值越小越靠前")
    void shouldSortByPriorityAscending() {
        PromptContext context = PromptContext.builder()
                .modeLock("MODE_LOCK (priority 0)")
                .worldState("WORLD_STATE (priority 10)")
                .modeHint("MODE_HINT (priority 90)")
                .plan("PLAN (priority 80)")
                .memory("MEMORY (priority 60)")
                .build();

        List<PromptLayer> layers = context.toLayers();
        List<String> sortedNames = layers.stream()
                .sorted((a, b) -> Integer.compare(a.priority(), b.priority()))
                .map(PromptLayer::name)
                .toList();

        assertThat(sortedNames).containsExactly(
                "MODE_LOCK", "WORLD_STATE", "MEMORY", "PLAN", "MODE_HINT"
        );

        String rendered = assembler.render(layers);
        List<String> lines = rendered.lines().filter(l -> !l.isEmpty()).toList();
        assertThat(lines).hasSize(5);
        assertThat(lines.get(0)).contains("MODE_LOCK");
        assertThat(lines.get(4)).contains("MODE_HINT");
    }

    @Test
    @DisplayName("Case4: 没有重复 Layer - 每个层只出现一次")
    void shouldNotHaveDuplicateLayers() {
        PromptContext context = PromptContext.builder()
                .baseSystemPrompt("base")
                .identity("id")
                .character("char")
                .relationship("rel")
                .userProfile("user")
                .workspace("ws")
                .memory("mem")
                .build();

        List<PromptLayer> layers = context.toLayers();
        long distinctCount = layers.stream().map(PromptLayer::name).distinct().count();

        assertThat(distinctCount).isEqualTo(layers.size());
    }

    @Test
    @DisplayName("Case5: 全空 PromptContext 渲染结果为空")
    void shouldReturnEmptyWhenAllLayersEmpty() {
        PromptContext context = PromptContext.builder()
                .baseSystemPrompt(null)
                .identity(null)
                .character(null)
                .memory(null)
                .build();

        List<PromptLayer> layers = context.toLayers();
        String rendered = assembler.render(layers);

        assertThat(layers).isEmpty();
        assertThat(rendered).isEmpty();
    }

    @Test
    @DisplayName("Case6: 验证标准顺序符合设计预期")
    void shouldMatchExpectedDesignOrder() {
        PromptContext context = PromptContext.builder()
                .baseSystemPrompt("X")
                .identity("X")
                .character("X")
                .relationship("X")
                .groupContext("X")
                .userProfile("X")
                .workspace("X")
                .hostContext("X")
                .memory("X")
                .artifact("X")
                .plan("X")
                .modeHint("X")
                .build();

        List<PromptLayer> layers = context.toLayers();

        int prevPriority = -1;
        for (PromptLayer layer : layers) {
            assertThat(layer.priority()).isGreaterThan(prevPriority);
            prevPriority = layer.priority();
        }

        assertThat(layers.get(0).name()).isEqualTo("BASE_SYSTEM");
        assertThat(layers.get(0).priority()).isEqualTo(5);
        assertThat(getLayerByName(layers, "IDENTITY").priority()).isEqualTo(20);
        assertThat(getLayerByName(layers, "RELATIONSHIP").priority()).isEqualTo(24);
        assertThat(getLayerByName(layers, "WORKSPACE").priority()).isEqualTo(50);
        assertThat(getLayerByName(layers, "HOST_CONTEXT").priority()).isEqualTo(55);
        assertThat(getLayerByName(layers, "MEMORY").priority()).isEqualTo(60);
        assertThat(getLayerByName(layers, "ARTIFACT").priority()).isEqualTo(65);
        assertThat(getLayerByName(layers, "PLAN").priority()).isEqualTo(80);
        assertThat(layers.get(layers.size() - 1).name()).isEqualTo("MODE_HINT");
        assertThat(layers.get(layers.size() - 1).priority()).isEqualTo(90);
    }

    private PromptLayer getLayerByName(List<PromptLayer> layers, String name) {
        return layers.stream().filter(l -> l.name().equals(name)).findFirst().orElseThrow();
    }
}