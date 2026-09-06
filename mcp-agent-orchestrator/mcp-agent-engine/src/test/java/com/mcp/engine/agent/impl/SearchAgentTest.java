package com.mcp.engine.agent.impl;

import com.mcp.common.channel.SearchRequirement;
import com.mcp.common.identity.MemoryIdentity;
import com.mcp.engine.agent.LLMRequest;
import com.mcp.engine.execution.ExecutionPlan;
import com.mcp.engine.execution.ExecutionState;
import com.mcp.engine.policy.PolicyEngine;
import com.mcp.engine.runtime.AgentRuntime;
import com.mcp.llm.client.ChatMessage;
import com.mcp.llm.client.LlmClient;
import com.mcp.llm.client.LlmToolResponse;
import com.mcp.tools.executor.ToolExecutor;
import com.mcp.tools.model.ToolDefinition;
import com.mcp.tools.model.ToolExecutionRequest;
import com.mcp.tools.model.ToolExecutionResult;
import com.mcp.tools.registry.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * SearchAgent 核心测试 — 验证搜索需求强制执行机制。
 *
 * 测试目标：
 * - REQUIRED 搜索 → LLM 不调用工具时，代码层确定性回退执行 deep_research
 * - NONE 搜索 → 接受 LLM 纯文本响应，不强制执行工具
 * - OPTIONAL 搜索 → 重试一次后接受 LLM 响应
 * - Tool Authorization 被拒绝时正确处理
 * - 最大轮次 + REQUIRED → 确定性回退
 * - 日期上下文注入到统一 Context
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchAgentTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private AgentRuntime agentRuntime;

    @Mock
    private ToolExecutor toolExecutor;

    @Mock
    private PolicyEngine policyEngine;

    @Mock
    private ResearchSynthesizer researchSynthesizer;

    private SearchAgent searchAgent;

    private static final String SESSION_ID = "test-session-001";
    private static final String USER_ID = "test-user-001";
    private static final String EXECUTION_ID = "exec-001";

    @BeforeEach
    void setUp() {
        searchAgent = new SearchAgent(
                llmClient, toolRegistry, agentRuntime,
                toolExecutor, policyEngine, researchSynthesizer);

        when(toolRegistry.getAllTools()).thenReturn(List.of(
                ToolDefinition.builder()
                        .name("deep_research")
                        .description("深度联网搜索")
                        .inputSchema("{\"properties\":{\"query\":{\"type\":\"string\"},\"depth\":{\"type\":\"string\"}},\"required\":[\"query\"]}")
                        .enabled(true)
                        .build(),
                ToolDefinition.builder()
                        .name("web_search")
                        .description("基础网页搜索")
                        .inputSchema("{\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}")
                        .enabled(true)
                        .build(),
                ToolDefinition.builder()
                        .name("multi_search")
                        .description("多搜索引擎并行搜索")
                        .inputSchema("{\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}")
                        .enabled(true)
                        .build(),
                ToolDefinition.builder()
                        .name("fetch_webpage")
                        .description("抓取网页内容")
                        .inputSchema("{\"properties\":{\"url\":{\"type\":\"string\"}},\"required\":[\"url\"]}")
                        .enabled(true)
                        .build()
        ));

        when(policyEngine.evaluate(any(), any(), any(), any(), any()))
                .thenReturn(PolicyEngine.PolicyDecision.ALLOW);
    }

    private LLMRequest buildRequest(SearchRequirement requirement) {
        ExecutionState execState = new ExecutionState(EXECUTION_ID);

        return LLMRequest.builder()
                .sessionId(SESSION_ID)
                .userId(USER_ID)
                .systemPrompt("你是一个搜索助手。")
                .userMessage("今天有什么科技新闻？")
                .searchRequirement(requirement)
                .executionPlan(ExecutionPlan.builder()
                        .identity(new MemoryIdentity(null, SESSION_ID, USER_ID, null, null))
                        .build())
                .executionState(execState)
                .build();
    }

    @Nested
    @DisplayName("SearchRequirement.REQUIRED — 确定性搜索强制执行")
    class RequiredSearchTests {

        @Test
        @DisplayName("LLM 不调用工具 → 代码层确定性回退执行 deep_research")
        void shouldExecuteDeterministicFallbackWhenLlmDoesNotCallTools() {
            LLMRequest request = buildRequest(SearchRequirement.REQUIRED);

            when(llmClient.chatWithTools(anyList(), anyList()))
                    .thenReturn(Mono.just(new LlmToolResponse("让我想想...", List.of())));

            ToolExecutionResult fallbackResult = ToolExecutionResult.success(
                    "fallback-001", "deep_research",
                    "{\"content\":\"{\\\"results\\\":[{\\\"title\\\":\\\"今日科技新闻\\\",\\\"snippet\\\":\\\"AI最新进展...\\\"}]}\"}",
                    Duration.ZERO);
            when(toolExecutor.execute(any(ToolExecutionRequest.class)))
                    .thenReturn(Mono.just(fallbackResult));

            when(toolRegistry.getTool("deep_research"))
                    .thenReturn(Mono.just(ToolDefinition.builder()
                            .name("deep_research")
                            .description("深度搜索")
                            .inputSchema("{}")
                            .build()));

            when(researchSynthesizer.synthesize(anyString(), anyList(), anyList()))
                    .thenReturn(Mono.just("确定性搜索回退：搜索完成"));

            StepVerifier.create(searchAgent.execute(request))
                    .assertNext(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response).contains("确定性搜索回退");
                    })
                    .verifyComplete();

            verify(llmClient, atLeastOnce()).chatWithTools(anyList(), anyList());
            verify(toolExecutor, atLeastOnce()).execute(any(ToolExecutionRequest.class));
        }

        @Test
        @DisplayName("LLM 正常调用工具 → 正常执行，不触发回退")
        void shouldNotFallbackWhenLlmCallsTools() {
            LLMRequest request = buildRequest(SearchRequirement.REQUIRED);

            LlmToolResponse.ToolCall toolCall = new LlmToolResponse.ToolCall(
                    "call-001", "deep_research",
                    Map.of("query", "今天有什么科技新闻？", "depth", "2"));

            when(llmClient.chatWithTools(anyList(), anyList()))
                    .thenReturn(Mono.just(new LlmToolResponse("", List.of(toolCall))))
                    .thenReturn(Mono.just(new LlmToolResponse("根据搜索结果，今天主要的科技新闻包括...", List.of())));

            ToolExecutionResult execResult = ToolExecutionResult.success(
                    "call-001", "deep_research",
                    "{\"content\":\"{\\\"results\\\":[{\\\"title\\\":\\\"科技新闻\\\",\\\"snippet\\\":\\\"test\\\"}]}\"}",
                    Duration.ZERO);
            when(toolExecutor.execute(any(ToolExecutionRequest.class)))
                    .thenReturn(Mono.just(execResult));

            when(toolRegistry.getTool("deep_research"))
                    .thenReturn(Mono.just(ToolDefinition.builder()
                            .name("deep_research")
                            .description("深度搜索")
                            .inputSchema("{}")
                            .build()));

            when(researchSynthesizer.synthesize(anyString(), anyList(), anyList()))
                    .thenReturn(Mono.just("合成后的搜索结果"));

            StepVerifier.create(searchAgent.execute(request))
                    .assertNext(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response).contains("搜索结果");
                    })
                    .verifyComplete();

            verify(llmClient, atLeast(2)).chatWithTools(anyList(), anyList());
            verify(toolExecutor, atLeastOnce()).execute(any(ToolExecutionRequest.class));
        }
    }

    @Nested
    @DisplayName("SearchRequirement.NONE — 不强制执行搜索")
    class NoneSearchTests {

        @Test
        @DisplayName("LLM 返回纯文本 → 直接接受，不触发工具调用")
        void shouldAcceptTextResponseWithoutToolCall() {
            LLMRequest request = buildRequest(SearchRequirement.NONE);

            when(llmClient.chatWithTools(anyList(), anyList()))
                    .thenReturn(Mono.just(new LlmToolResponse("HashMap是一种基于哈希表实现的Map接口...", List.of())));

            StepVerifier.create(searchAgent.execute(request))
                    .assertNext(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response).contains("HashMap");
                    })
                    .verifyComplete();

            verify(llmClient, atLeastOnce()).chatWithTools(anyList(), anyList());
            verify(toolExecutor, never()).execute(any(ToolExecutionRequest.class));
        }
    }

    @Nested
    @DisplayName("SearchRequirement.OPTIONAL — 温和重试，不强制")
    class OptionalSearchTests {

        @Test
        @DisplayName("LLM 不调用工具 → 重试一次后接受文本响应")
        void shouldRetryOnceThenAcceptWhenOptional() {
            LLMRequest request = buildRequest(SearchRequirement.OPTIONAL);

            when(llmClient.chatWithTools(anyList(), anyList()))
                    .thenReturn(Mono.just(new LlmToolResponse("这是一个技术问题...", List.of())))
                    .thenReturn(Mono.just(new LlmToolResponse("根据我的知识，答案是...", List.of())));

            StepVerifier.create(searchAgent.execute(request))
                    .assertNext(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response).contains("根据我的知识");
                    })
                    .verifyComplete();

            verify(llmClient, times(2)).chatWithTools(anyList(), anyList());
            verify(toolExecutor, never()).execute(any(ToolExecutionRequest.class));
        }
    }

    @Nested
    @DisplayName("Tool Authorization — 工具授权被拒绝")
    class ToolAuthorizationTests {

        @Test
        @DisplayName("PolicyEngine DENY → 工具调用被拒绝，记录日志")
        void shouldHandleToolDenialGracefully() {
            LLMRequest request = buildRequest(SearchRequirement.REQUIRED);

            LlmToolResponse.ToolCall toolCall = new LlmToolResponse.ToolCall(
                    "call-001", "deep_research",
                    Map.of("query", "test", "depth", "2"));

            when(llmClient.chatWithTools(anyList(), anyList()))
                    .thenReturn(Mono.just(new LlmToolResponse("", List.of(toolCall))));

            when(toolRegistry.getTool("deep_research"))
                    .thenReturn(Mono.just(ToolDefinition.builder()
                            .name("deep_research")
                            .description("深度搜索")
                            .inputSchema("{}")
                            .build()));

            when(policyEngine.evaluate(any(), any(), any(), any(), any()))
                    .thenReturn(PolicyEngine.PolicyDecision.DENY);

            when(researchSynthesizer.synthesize(anyString(), anyList(), anyList()))
                    .thenReturn(Mono.just("无搜索结果"));

            StepVerifier.create(searchAgent.execute(request))
                    .assertNext(response -> {
                        assertThat(response).isNotNull();
                    })
                    .verifyComplete();

            verify(toolExecutor, never()).execute(any(ToolExecutionRequest.class));
        }
    }

    @Nested
    @DisplayName("最大轮次 + REQUIRED → 确定性回退")
    class MaxRoundsFallbackTests {

        @Test
        @DisplayName("达到最大轮次且无工具结果 → 确定性回退执行 deep_research")
        void shouldFallbackWhenMaxRoundsReachedWithNoResults() {
            LLMRequest request = buildRequest(SearchRequirement.REQUIRED);

            when(llmClient.chatWithTools(anyList(), anyList()))
                    .thenReturn(Mono.just(new LlmToolResponse("让我思考...", List.of())));

            ToolExecutionResult fallbackResult = ToolExecutionResult.success(
                    "fallback-002", "deep_research",
                    "{\"content\":\"{\\\"results\\\":[{\\\"title\\\":\\\"新闻\\\",\\\"snippet\\\":\\\"内容\\\"}]}\"}",
                    Duration.ZERO);
            when(toolExecutor.execute(any(ToolExecutionRequest.class)))
                    .thenReturn(Mono.just(fallbackResult));

            when(toolRegistry.getTool("deep_research"))
                    .thenReturn(Mono.just(ToolDefinition.builder()
                            .name("deep_research")
                            .description("深度搜索")
                            .inputSchema("{}")
                            .build()));

            StepVerifier.create(searchAgent.execute(request))
                    .assertNext(response -> {
                        assertThat(response).isNotNull();
                    })
                    .verifyComplete();

            verify(toolExecutor, atLeastOnce()).execute(any(ToolExecutionRequest.class));
        }
    }

    @Nested
    @DisplayName("无 ToolExecutor → 优雅降级")
    class NoToolExecutorTests {

        @Test
        @DisplayName("REQUIRED 搜索 + 无 ToolExecutor → 返回明确错误")
        void shouldReturnErrorWhenRequiredAndNoToolExecutor() {
            SearchAgent agentWithoutExecutor = new SearchAgent(
                    llmClient, toolRegistry, agentRuntime,
                    null, policyEngine, researchSynthesizer);

            LLMRequest request = buildRequest(SearchRequirement.REQUIRED);

            StepVerifier.create(agentWithoutExecutor.execute(request))
                    .assertNext(response -> {
                        assertThat(response).contains("未配置搜索工具");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("NONE 搜索 + 无 ToolExecutor → 回退到纯文本模式")
        void shouldFallbackToTextModeWhenNoneAndNoToolExecutor() {
            SearchAgent agentWithoutExecutor = new SearchAgent(
                    llmClient, toolRegistry, agentRuntime,
                    null, policyEngine, researchSynthesizer);

            LLMRequest request = buildRequest(SearchRequirement.NONE);

            when(agentRuntime.run(anyString(), anyString()))
                    .thenReturn(Mono.just("根据我的知识，HashMap是..."));

            StepVerifier.create(agentWithoutExecutor.execute(request))
                    .assertNext(response -> {
                        assertThat(response).contains("HashMap");
                    })
                    .verifyComplete();
        }
    }
}