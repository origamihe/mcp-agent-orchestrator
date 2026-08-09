package com.mcp.engine.test.context;

import com.mcp.common.identity.GroupContext;
import com.mcp.common.identity.GroupMember;
import com.mcp.common.identity.UserProfile;
import com.mcp.core.context.BuildContext;
import com.mcp.core.context.ContextAssembler;
import com.mcp.core.context.ContextProvider;
import com.mcp.core.context.PromptContext;
import com.mcp.core.context.PromptContextBuilder;
import com.mcp.core.context.PromptLayer;
import com.mcp.core.context.provider.HostContextProvider;
import com.mcp.core.context.provider.IdentityContextProvider;
import com.mcp.core.context.provider.RelationshipContextProvider;
import com.mcp.core.context.provider.WorkspaceContextProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1 Context Pipeline - 验证上下文流水线完整性
 *
 * 验证路径：
 * RequestContext → BuildContext → ContextProvider → PromptContext → LLM
 *
 * 测试目标：
 * - 验证各个 ContextProvider 是否正确读取 BuildContext
 * - 验证各个 Provider 是否正确填充 PromptContext 对应字段
 * - 验证最终组装的 Prompt 包含所有预期层
 * - 验证空字段不生成空 Layer
 */
@ExtendWith(MockitoExtension.class)
class T1_ContextPipelineTest {

    private IdentityContextProvider identityProvider;
    private RelationshipContextProvider relationshipProvider;
    private WorkspaceContextProvider workspaceProvider;
    private HostContextProvider hostProvider;
    private PromptContextBuilder promptContextBuilder;
    private ContextAssembler contextAssembler;

    @BeforeEach
    void setUp() {
        identityProvider = new IdentityContextProvider();
        relationshipProvider = new RelationshipContextProvider();
        workspaceProvider = new WorkspaceContextProvider();
        hostProvider = new HostContextProvider();

        List<ContextProvider> providers = List.of(
                identityProvider,
                relationshipProvider,
                workspaceProvider,
                hostProvider
        );
        promptContextBuilder = new PromptContextBuilder(providers);
        contextAssembler = new ContextAssembler();
    }

    @Test
    @DisplayName("Case1: 记住用户偏好 - 验证 Relationship + Identity + Memory 全部生成")
    void shouldGenerateAllLayersWhenUserPreferenceProvided() {
        UserProfile userProfile = UserProfile.builder()
                .userId("user-1")
                .nickname("RiKo")
                .build();

        BuildContext ctx = BuildContext.builder()
                .baseSystemPrompt("你是一个有用的助手。")
                .personaPrompt("""
                【核心身份】
                我是澪音，一个智能助手。

                【行为准则】
                保持友好，回答简洁。

                【用户偏好】
                用户最喜欢 Terraria。
                """)
                .userMessage("你记得我喜欢什么吗？")
                .userProfile(userProfile)
                .build();

        PromptContext promptContext = promptContextBuilder.build(ctx);

        assertThat(promptContext.getBaseSystemPrompt()).isEqualTo("你是一个有用的助手。");
        assertThat(promptContext.getIdentity()).contains("澪音");
        assertThat(promptContext.getCharacter()).contains("保持友好");
        assertThat(promptContext.getUserProfile()).contains("RiKo");
        assertThat(promptContext.getIdentity()).isNotEmpty();

        List<PromptLayer> layers = promptContext.toLayers();
        assertThat(layers.stream().map(PromptLayer::name))
                .contains("BASE_SYSTEM", "IDENTITY", "CHARACTER", "USER_PROFILE");

        String rendered = contextAssembler.render(layers);
        assertThat(rendered).contains("澪音", "RiKo");
    }

    @Test
    @DisplayName("Case2: 用户自我介绍 - 检查 UserProfile 和 Identity 是否一致")
    void shouldCorrectlySetUserProfileAndIdentity() {
        UserProfile userProfile = UserProfile.builder()
                .userId("riko-123")
                .nickname("RiKo")
                .build();

        BuildContext ctx = BuildContext.builder()
                .baseSystemPrompt("你是助手。")
                .personaPrompt("""
                【核心身份】
                我是澪音，一个 AI 助手。
                """)
                .userMessage("我是 RiKo，以后叫我 RiKo。")
                .userProfile(userProfile)
                .build();

        PromptContext promptContext = promptContextBuilder.build(ctx);
        List<PromptLayer> layers = promptContext.toLayers();

        assertThat(promptContext.getUserProfile()).contains("RiKo");
        assertThat(promptContext.getUserProfile()).contains("riko-123");
        assertThat(layers.stream().map(PromptLayer::name))
                .contains("USER_PROFILE");

        String rendered = contextAssembler.render(layers);
        assertThat(rendered).contains("当前用户信息", "RiKo");
    }

    @Test
    @DisplayName("Case3: 群聊场景 - 检查 GroupContext 是否正确填充")
    void shouldCorrectlyPopulateGroupContext() {
        GroupMember admin = GroupMember.builder()
                .userId("riko")
                .displayName("RiKo")
                .role("ADMIN")
                .build();
        GroupMember alice = GroupMember.builder()
                .userId("alice")
                .displayName("Alice")
                .role("MEMBER")
                .build();
        GroupMember bob = GroupMember.builder()
                .userId("bob")
                .displayName("Bob")
                .role("NEWBIE")
                .build();

        GroupContext groupContext = GroupContext.builder()
                .groupId("group-123")
                .groupName("测试群")
                .members(List.of(admin, alice, bob))
                .build();

        BuildContext ctx = BuildContext.builder()
                .baseSystemPrompt("你是助手。")
                .personaPrompt("【核心身份】我是澪音")
                .userMessage("Alice 是谁？Bob 是谁？")
                .groupContext(groupContext)
                .build();

        PromptContext promptContext = promptContextBuilder.build(ctx);
        List<PromptLayer> layers = promptContext.toLayers();

        assertThat(promptContext.getGroupContext()).isNotEmpty();
        assertThat(layers.stream().map(PromptLayer::name))
                .contains("GROUP_CONTEXT");

        String rendered = contextAssembler.render(layers);
        assertThat(rendered).contains("当前群信息", "测试群", "Alice", "Bob", "ADMIN");
    }

    @Test
    @DisplayName("Case4: 空上下文 - 只包含 BASE_SYSTEM，其他层为空")
    void shouldOnlyContainBaseSystemWhenEmptyContext() {
        BuildContext ctx = BuildContext.builder()
                .baseSystemPrompt("这是基础提示词")
                .userMessage("你好")
                .build();

        PromptContext promptContext = promptContextBuilder.build(ctx);
        List<PromptLayer> layers = promptContext.toLayers();

        assertThat(promptContext.getIdentity()).isNotEmpty();
        assertThat(promptContext.getGroupContext()).isNull();
        assertThat(promptContext.getWorkspace()).isNull();
        assertThat(promptContext.getHostContext()).isNull();

        assertThat(layers.stream().map(PromptLayer::name))
                .contains("BASE_SYSTEM")
                .doesNotContain("GROUP_CONTEXT", "WORKSPACE", "HOST_CONTEXT");
    }

    @Test
    @DisplayName("Case5: 工作空间上下文 - Workspace 层正确填充")
    void shouldCorrectlyPopulateWorkspaceLayer() {
        String workspacePrompt = """
                【当前工作空间】
                项目名: mcp-agent-orchestrator
                模块: mcp-core, mcp-agent-engine, mcp-tools
                活跃任务: 重构上下文流水线
                """;

        BuildContext ctx = BuildContext.builder()
                .baseSystemPrompt("你是助手。")
                .userMessage("现在有哪些模块？")
                .workspacePrompt(workspacePrompt)
                .build();

        PromptContext promptContext = promptContextBuilder.build(ctx);

        assertThat(promptContext.getWorkspace()).isEqualTo(workspacePrompt);

        List<PromptLayer> layers = promptContext.toLayers();
        assertThat(layers.stream().map(PromptLayer::name))
                .contains("WORKSPACE");
    }

    @Test
    @DisplayName("Case6: Host 上下文 - HostContext 层正确填充")
    void shouldCorrectlyPopulateHostContextLayer() {
        String hostPrompt = """
                【当前 Host 上下文】
                打开文件: BuildContext.java
                当前行: 43
                Git 变更: 3 files changed
                """;

        BuildContext ctx = BuildContext.builder()
                .baseSystemPrompt("你是助手。")
                .userMessage("我现在在哪里？")
                .hostContextPrompt(hostPrompt)
                .build();

        PromptContext promptContext = promptContextBuilder.build(ctx);

        assertThat(promptContext.getHostContext()).isEqualTo(hostPrompt);

        List<PromptLayer> layers = promptContext.toLayers();
        assertThat(layers.stream().map(PromptLayer::name))
                .contains("HOST_CONTEXT");
    }

    @Test
    @DisplayName("Case7: 全字段填充 - 所有 Provider 同时工作，无遗漏")
    void shouldFillAllLayersWhenAllFieldsProvided() {
        UserProfile userProfile = UserProfile.builder()
                .userId("user-1")
                .preferredName("User")
                .build();

        GroupContext groupContext = GroupContext.builder()
                .groupId("g1")
                .groupName("Group")
                .members(List.of())
                .build();

        BuildContext ctx = BuildContext.builder()
                .baseSystemPrompt("BASE")
                .developerPrompt("DEV")
                .personaPrompt("""
                【核心身份】我是澪音
                【行为准则】保持友好
                """)
                .userMessage("USER")
                .userProfile(userProfile)
                .groupContext(groupContext)
                .workspacePrompt("WORKSPACE")
                .hostContextPrompt("HOST")
                .build();

        PromptContext promptContext = promptContextBuilder.build(ctx);
        List<PromptLayer> layers = promptContext.toLayers();

        assertThat(promptContext.getBaseSystemPrompt()).isEqualTo("BASE");
        assertThat(promptContext.getIdentity()).contains("澪音");
        assertThat(promptContext.getUserProfile()).contains("User");
        assertThat(promptContext.getGroupContext()).isNotEmpty();
        assertThat(promptContext.getWorkspace()).isEqualTo("WORKSPACE");
        assertThat(promptContext.getHostContext()).isEqualTo("HOST");

        List<String> layerNames = layers.stream().map(PromptLayer::name).toList();
        assertThat(layerNames)
                .contains("BASE_SYSTEM", "IDENTITY", "CHARACTER", "GROUP_CONTEXT",
                        "USER_PROFILE", "WORKSPACE", "HOST_CONTEXT");
    }

    @Test
    @DisplayName("Case8: 空内容不生成 Layer - 不会出现空内容层")
    void shouldNotGenerateLayerForEmptyContent() {
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