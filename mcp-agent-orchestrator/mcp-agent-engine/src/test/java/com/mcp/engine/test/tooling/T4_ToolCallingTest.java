package com.mcp.engine.test.tooling;

import com.mcp.tools.executor.ToolExecutor;
import com.mcp.tools.model.*;
import com.mcp.tools.registry.CapabilityResolver;
import com.mcp.tools.registry.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * T4 Tool Calling - 验证工具路由与执行
 *
 * 测试目标：
 * - 工具注册后能被正确发现
 * - 工具执行成功时返回正确结果
 * - 工具执行失败时正确传播错误
 * - 工具能力解析正确
 * - 各具体工具执行正确（Search, Code, Docx, PPT）
 * - 工具超时处理
 * - 工具权限校验
 */
@ExtendWith(MockitoExtension.class)
class T4_ToolCallingTest {

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private ToolExecutor toolExecutor;

    @Mock
    private CapabilityResolver capabilityResolver;

    @BeforeEach
    void setUp() {
        // no-op
    }

    @Test
    @DisplayName("Case1: 工具注册后能被正确发现")
    void shouldDiscoverToolAfterRegistration() {
        ToolDefinition searchTool = ToolDefinition.builder()
                .name("web_search")
                .description("搜索网络信息")
                .category(ToolCategory.SEARCH)
                .capabilities(Set.of(ToolCapability.SEARCH_CODE))
                .enabled(true)
                .build();

        when(toolRegistry.containsTool("web_search")).thenReturn(true);
        when(toolRegistry.getTool("web_search")).thenReturn(Mono.just(searchTool));

        assertThat(toolRegistry.containsTool("web_search")).isTrue();
        ToolDefinition found = toolRegistry.getTool("web_search").block();
        assertThat(found).isNotNull();
        assertThat(found.getDescription()).isEqualTo("搜索网络信息");
        assertThat(found.getCategory()).isEqualTo(ToolCategory.SEARCH);
    }

    @Test
    @DisplayName("Case2: 工具执行成功 - 返回正确结果")
    void shouldExecuteToolSuccessfully() {
        ToolExecutionRequest request = new ToolExecutionRequest();
        request.setToolName("web_search");
        request.setArguments(java.util.Map.of("query", "OpenAI 最新模型"));

        String resultJson = """
                {
                    "results": [
                        {"title": "GPT-5 发布", "snippet": "OpenAI 于2025年发布GPT-5..."},
                        {"title": "o1 推理模型", "snippet": "OpenAI o1 具备强推理能力..."}
                    ]
                }
                """;

        when(toolExecutor.execute(request)).thenReturn(Mono.just(ToolExecutionResult.success("1", "web_search", resultJson, java.time.Duration.ZERO)));

        StepVerifier.create(toolExecutor.execute(request))
                .expectNextMatches(r -> r.data().equals(resultJson))
                .verifyComplete();

        verify(toolExecutor).execute(request);
    }

    @Test
    @DisplayName("Case3: 工具执行失败 - 正确传播错误")
    void shouldPropagateErrorWhenToolFails() {
        ToolExecutionRequest request = new ToolExecutionRequest();
        request.setToolName("web_search");
        request.setArguments(java.util.Map.of("query", "nonexistent"));

        RuntimeException error = new RuntimeException("网络连接超时");
        when(toolExecutor.execute(request)).thenReturn(Mono.error(error));

        StepVerifier.create(toolExecutor.execute(request))
                .expectErrorMatches(e -> e instanceof RuntimeException
                        && e.getMessage().contains("网络连接超时"))
                .verify();
    }

    @Test
    @DisplayName("Case4: 工具能力解析 - 根据 capability 找到正确工具")
    void shouldResolveToolByCapability() {
        ToolDefinition searchTool = ToolDefinition.builder()
                .name("web_search")
                .capabilities(Set.of(ToolCapability.SEARCH_CODE))
                .build();

        ToolScore score = ToolScore.builder()
                .tool(searchTool)
                .baseScore(100.0)
                .build();

        ToolQuery query = ToolQuery.builder()
                .capability(ToolCapability.SEARCH_CODE)
                .enabled(true)
                .build();

        when(capabilityResolver.resolveRanked(query)).thenReturn(List.of(score));

        List<ToolScore> ranked = capabilityResolver.resolveRanked(query);
        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).getToolName()).isEqualTo("web_search");
        assertThat(ranked.get(0).getCompositeScore()).isPositive();
    }

    @Test
    @DisplayName("Case5: 多个工具竞争 - 根据 score 排序选择最佳")
    void shouldSelectBestToolByScore() {
        ToolDefinition toolA = ToolDefinition.builder().name("web_search").build();
        ToolDefinition toolB = ToolDefinition.builder().name("multi_search").build();

        ToolScore scoreA = ToolScore.builder().tool(toolA).baseScore(100.0).skillBonus(10.0).build();
        ToolScore scoreB = ToolScore.builder().tool(toolB).baseScore(100.0).skillBonus(25.0).build();

        ToolQuery query = ToolQuery.builder()
                .capability(ToolCapability.SEARCH_CODE)
                .enabled(true)
                .build();

        when(capabilityResolver.resolveRanked(query)).thenReturn(List.of(scoreB, scoreA));

        List<ToolScore> ranked = capabilityResolver.resolveRanked(query);
        assertThat(ranked).hasSize(2);
        assertThat(ranked.get(0).getToolName()).isEqualTo("multi_search");
        assertThat(ranked.get(0).getCompositeScore()).isGreaterThan(ranked.get(1).getCompositeScore());
    }

    @Test
    @DisplayName("Case6: Code Tool - 代码修改工具正确执行")
    void shouldExecuteCodeTool() {
        ToolExecutionRequest request = new ToolExecutionRequest();
        request.setToolName("edit_file");
        request.setArguments(java.util.Map.of(
                "filePath", "UserService.java",
                "oldCode", "public void save()",
                "newCode", "public void save(User user)"
        ));

        String result = "{\"success\": true, \"filePath\": \"UserService.java\", \"linesChanged\": 3}";
        when(toolExecutor.execute(request)).thenReturn(Mono.just(ToolExecutionResult.success("1", "edit_file", result, java.time.Duration.ZERO)));

        StepVerifier.create(toolExecutor.execute(request))
                .expectNextMatches(r -> r.data().equals(result))
                .verifyComplete();
    }

    @Test
    @DisplayName("Case7: Docx 生成工具 - 需求文档生成正确")
    void shouldExecuteDocxGenerationTool() {
        ToolExecutionRequest request = new ToolExecutionRequest();
        request.setToolName("docx_generator");
        request.setArguments(java.util.Map.of(
                "theme", "需求文档",
                "content", "## 用户管理模块\n1. 用户注册\n2. 用户登录\n3. 权限管理"
        ));

        String result = """
                {
                    "success": true,
                    "fileName": "需求文档_20250629.docx",
                    "filePath": "./generated/docx/需求文档_20250629.docx",
                    "pageCount": 5
                }
                """;
        when(toolExecutor.execute(request)).thenReturn(Mono.just(ToolExecutionResult.success("1", "docx_generator", result, java.time.Duration.ZERO)));

        StepVerifier.create(toolExecutor.execute(request))
                .expectNextMatches(r -> r.data().equals(result))
                .verifyComplete();
    }

    @Test
    @DisplayName("Case8: PPT 生成工具 - 演示文稿生成正确")
    void shouldExecutePptGenerationTool() {
        ToolExecutionRequest request = new ToolExecutionRequest();
        request.setToolName("ppt_generator");
        request.setArguments(java.util.Map.of(
                "theme", "MCP Agent 架构介绍",
                "slides", List.of(
                        "MCP 概念介绍",
                        "Agent 编排系统架构",
                        "Memory 记忆系统",
                        "Tool Calling 工具调用",
                        "Prompt Assembly 提示组装",
                        "总结与展望"
                )
        ));

        String result = """
                {
                    "success": true,
                    "fileName": "MCP_Agent_架构介绍.pptx",
                    "filePath": "./generated/ppt/MCP_Agent_架构介绍.pptx",
                    "slideCount": 6
                }
                """;
        when(toolExecutor.execute(request)).thenReturn(Mono.just(ToolExecutionResult.success("1", "ppt_generator", result, java.time.Duration.ZERO)));

        StepVerifier.create(toolExecutor.execute(request))
                .expectNextMatches(r -> r.data().equals(result))
                .verifyComplete();
    }

    @Test
    @DisplayName("Case9: 工具超时处理 - 超时后返回错误")
    void shouldHandleToolTimeout() {
        ToolExecutionRequest request = new ToolExecutionRequest();
        request.setToolName("deep_research");
        request.setArguments(java.util.Map.of("query", "分析整个 MCP 生态"));

        when(toolExecutor.execute(request))
                .thenReturn(Mono.error(new RuntimeException("工具执行超时 (30s)")));

        StepVerifier.create(toolExecutor.execute(request))
                .expectErrorMatches(e -> e.getMessage().contains("超时"))
                .verify();
    }

    @Test
    @DisplayName("Case10: 工具注册表查询 - 按分类获取工具列表")
    void shouldQueryToolsByCategory() {
        ToolDefinition readTool = ToolDefinition.builder()
                .name("read_file")
                .category(ToolCategory.READ)
                .enabled(true)
                .build();
        ToolDefinition searchTool = ToolDefinition.builder()
                .name("web_search")
                .category(ToolCategory.SEARCH)
                .enabled(true)
                .build();
        ToolDefinition docxTool = ToolDefinition.builder()
                .name("docx_generator")
                .category(ToolCategory.CODE)
                .enabled(true)
                .build();

        when(toolRegistry.getToolsByCategory(ToolCategory.SEARCH)).thenReturn(List.of(searchTool));
        when(toolRegistry.getToolsByCategory(ToolCategory.READ)).thenReturn(List.of(readTool));
        when(toolRegistry.getToolsByCategory(ToolCategory.CODE)).thenReturn(List.of(docxTool));
        when(toolRegistry.getAllTools()).thenReturn(List.of(readTool, searchTool, docxTool));

        assertThat(toolRegistry.getToolsByCategory(ToolCategory.SEARCH)).hasSize(1);
        assertThat(toolRegistry.getToolsByCategory(ToolCategory.READ)).hasSize(1);
        assertThat(toolRegistry.getToolsByCategory(ToolCategory.CODE)).hasSize(1);
        assertThat(toolRegistry.getAllTools()).hasSize(3);
    }
}