package com.mcp.engine.agent;

import com.mcp.common.agent.MultiAgentContext;
import com.mcp.engine.agent.card.AgentCard;
import com.mcp.engine.agent.registry.AgentRegistry;
import com.mcp.engine.orchestrator.MultiAgentOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * P9 验证 — MultiAgentManager / MultiAgentContext 测试。
 * 验证：
 * 1. MultiAgentContext 模型与 Prompt 片段生成
 * 2. MultiAgentManager 上下文构建
 * 3. MultiAgentManager 统计功能
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P9 — Multi-Agent")
class MultiAgentManagerTest {

    @Mock
    private MultiAgentOrchestrator orchestrator;
    @Mock
    private AgentRegistry agentRegistry;

    private MultiAgentManager multiAgentManager;

    @BeforeEach
    void setUp() {
        multiAgentManager = new MultiAgentManager(orchestrator, agentRegistry);
    }

    // ==================== MultiAgentContext 模型 ====================

    @Nested
    @DisplayName("MultiAgentContext 模型")
    class MultiAgentContextModel {

        @Test
        @DisplayName("空上下文应返回空字符串")
        void shouldReturnEmptyForEmptyContext() {
            MultiAgentContext ctx = MultiAgentContext.builder().build();

            assertThat(ctx.isEmpty()).isTrue();
            assertThat(ctx.toPromptFragment()).isEmpty();
        }

        @Test
        @DisplayName("应生成正确的 Prompt 片段")
        void shouldGeneratePromptFragment() {
            MultiAgentContext.AgentInfo searchAgent = MultiAgentContext.AgentInfo.of(
                    "agent-1", "SearchAgent", "SEARCH", List.of("web_search", "news_search"));
            MultiAgentContext.AgentInfo codeAgent = MultiAgentContext.AgentInfo.of(
                    "agent-2", "CodeAgent", "CODE", List.of("code_gen", "code_review"));

            MultiAgentContext ctx = MultiAgentContext.builder()
                    .matchedAgents(List.of(searchAgent))
                    .availableAgents(List.of(searchAgent, codeAgent))
                    .totalAgents(2)
                    .build();

            assertThat(ctx.isEmpty()).isFalse();
            String fragment = ctx.toPromptFragment();
            assertThat(fragment).contains("可用协作 Agent");
            assertThat(fragment).contains("SearchAgent");
            assertThat(fragment).contains("web_search");
            assertThat(fragment).contains("在线 Agent 列表");
            assertThat(fragment).contains("CodeAgent");
        }

        @Test
        @DisplayName("AgentInfo 应正确构建")
        void shouldBuildAgentInfo() {
            MultiAgentContext.AgentInfo info = MultiAgentContext.AgentInfo.of(
                    "agent-1", "TestAgent", "CHAT", List.of("chat"));

            assertThat(info.getId()).isEqualTo("agent-1");
            assertThat(info.getName()).isEqualTo("TestAgent");
            assertThat(info.getType()).isEqualTo("CHAT");
            assertThat(info.getSkills()).containsExactly("chat");
        }
    }

    // ==================== MultiAgentManager ====================

    @Nested
    @DisplayName("MultiAgentManager")
    class MultiAgentManagerTests {

        @Test
        @DisplayName("应构建多 Agent 上下文")
        void shouldBuildMultiAgentContext() {
            AgentCard card = AgentCard.builder()
                    .agentId("agent-1")
                    .agentName("SearchAgent")
                    .agentType(AgentCard.AgentType.SEARCH)
                    .description("搜索代理")
                    .skills(List.of("web_search", "news_search"))
                    .build();

            when(orchestrator.getAllAgentCards()).thenReturn(List.of(card));
            when(orchestrator.getAgentCount()).thenReturn(1);
            when(agentRegistry.matchBySkills(List.of("web_search")))
                    .thenReturn(List.of(new AgentRegistry.AgentMatch(
                            "agent-1", card, List.of("web_search"), 0.5)));

            MultiAgentContext ctx = multiAgentManager.buildMultiAgentContext(
                    "搜索新闻", List.of("web_search"));

            assertThat(ctx.getAvailableAgents()).hasSize(1);
            assertThat(ctx.getAvailableAgents().get(0).getName()).isEqualTo("SearchAgent");
            assertThat(ctx.getMatchedAgents()).hasSize(1);
            assertThat(ctx.getMatchedAgents().get(0).getMatchScore()).isEqualTo(0.5);
            assertThat(ctx.getTotalAgents()).isEqualTo(1);
        }

        @Test
        @DisplayName("无技能要求时应返回空匹配列表")
        void shouldReturnEmptyMatchWhenNoSkills() {
            AgentCard card = AgentCard.builder()
                    .agentId("agent-1")
                    .agentName("SearchAgent")
                    .agentType(AgentCard.AgentType.SEARCH)
                    .skills(List.of("web_search"))
                    .build();

            when(orchestrator.getAllAgentCards()).thenReturn(List.of(card));
            when(orchestrator.getAgentCount()).thenReturn(1);

            MultiAgentContext ctx = multiAgentManager.buildMultiAgentContext(
                    "hello", null);

            assertThat(ctx.getAvailableAgents()).hasSize(1);
            assertThat(ctx.getMatchedAgents()).isEmpty();
        }

        @Test
        @DisplayName("应正确统计 Agent 数据")
        void shouldReturnCorrectStats() {
            AgentCard chat = AgentCard.builder()
                    .agentId("a1").agentName("Chat").agentType(AgentCard.AgentType.CHAT).build();
            AgentCard search = AgentCard.builder()
                    .agentId("a2").agentName("Search").agentType(AgentCard.AgentType.SEARCH).build();
            AgentCard code = AgentCard.builder()
                    .agentId("a3").agentName("Code").agentType(AgentCard.AgentType.CODE).build();

            when(orchestrator.getAllAgentCards()).thenReturn(List.of(chat, search, code));

            MultiAgentManager.MultiAgentStats stats = multiAgentManager.getStats();

            assertThat(stats.totalAgents()).isEqualTo(3);
            assertThat(stats.chatAgents()).isEqualTo(1);
            assertThat(stats.searchAgents()).isEqualTo(1);
            assertThat(stats.codeAgents()).isEqualTo(1);
            assertThat(stats.otherAgents()).isEqualTo(0);
        }
    }
}