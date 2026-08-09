package com.mcp.llm.provider;

import com.mcp.llm.client.LlmToolResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SpringAiLlmClient — 文本回退 Tool Call 解析")
class SpringAiLlmClientTest {

    /**
     * 构建 SearchAgent 的 4 个工具定义（与 log 中 Node2-MultiToolFilter 一致）
     */
    private static List<Map<String, Object>> buildSearchAgentToolDefinitions() {
        return List.of(
                Map.of("type", "function",
                        "function", Map.of(
                                "name", "deep_research",
                                "description", "深度研究工具",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "query", Map.of("type", "string", "description", "研究主题/问题"),
                                                "depth", Map.of("type", "string", "description", "搜索深度")
                                        ),
                                        "required", List.of("query")
                                )
                        )),
                Map.of("type", "function",
                        "function", Map.of(
                                "name", "multi_search",
                                "description", "多引擎并行搜索",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "query", Map.of("type", "string", "description", "搜索关键词")
                                        ),
                                        "required", List.of("query")
                                )
                        )),
                Map.of("type", "function",
                        "function", Map.of(
                                "name", "web_search",
                                "description", "联网搜索",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "query", Map.of("type", "string", "description", "搜索关键词")
                                        ),
                                        "required", List.of("query")
                                )
                        )),
                Map.of("type", "function",
                        "function", Map.of(
                                "name", "fetch_webpage",
                                "description", "抓取网页",
                                "parameters", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "url", Map.of("type", "string", "description", "网页链接")
                                        ),
                                        "required", List.of("url")
                                )
                        ))
        );
    }

    @Nested
    @DisplayName("从 log 中提取的真实模型输出")
    class RealWorldLogContent {

        @Test
        @DisplayName("解析 log 中 CALL_SEARCH_TOOL + JSON 代码块 → deep_research")
        void shouldParseCallSearchToolWithJsonBlock() {
            String content = "CALL_SEARCH_TOOL\n```json\n{\"query\": \"中国国内政策变化\", \"depth\": \"2\"}\n```";

            List<LlmToolResponse.ToolCall> result =
                    SpringAiLlmClient.tryParseTextToolCalls(content, buildSearchAgentToolDefinitions());

            assertThat(result).hasSize(1);
            LlmToolResponse.ToolCall call = result.get(0);
            assertThat(call.getName()).isEqualTo("deep_research");
            assertThat(call.getArguments())
                    .containsEntry("query", "中国国内政策变化")
                    .containsEntry("depth", "2");
        }
    }

    @Nested
    @DisplayName("边界条件")
    class EdgeCases {

        @Test
        @DisplayName("null content → 空列表")
        void nullContentReturnsEmpty() {
            List<LlmToolResponse.ToolCall> result =
                    SpringAiLlmClient.tryParseTextToolCalls(null, buildSearchAgentToolDefinitions());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("空字符串 content → 空列表")
        void emptyContentReturnsEmpty() {
            List<LlmToolResponse.ToolCall> result =
                    SpringAiLlmClient.tryParseTextToolCalls("", buildSearchAgentToolDefinitions());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null toolDefinitions → 空列表")
        void nullToolDefinitionsReturnsEmpty() {
            String content = "```json\n{\"query\": \"test\"}\n```";
            List<LlmToolResponse.ToolCall> result =
                    SpringAiLlmClient.tryParseTextToolCalls(content, null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("空 toolDefinitions → 空列表")
        void emptyToolDefinitionsReturnsEmpty() {
            String content = "```json\n{\"query\": \"test\"}\n```";
            List<LlmToolResponse.ToolCall> result =
                    SpringAiLlmClient.tryParseTextToolCalls(content, List.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("无 JSON 代码块的纯文本 → 空列表")
        void plainTextWithoutJsonBlockReturnsEmpty() {
            String content = "无法获取实时政策信息，建议通过中国政府官网查询。";
            List<LlmToolResponse.ToolCall> result =
                    SpringAiLlmClient.tryParseTextToolCalls(content, buildSearchAgentToolDefinitions());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("JSON 参数不匹配任何工具 → 空列表")
        void unmatchedJsonParamsReturnsEmpty() {
            String content = "```json\n{\"unknown_param\": \"value\"}\n```";
            List<LlmToolResponse.ToolCall> result =
                    SpringAiLlmClient.tryParseTextToolCalls(content, buildSearchAgentToolDefinitions());
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("匹配策略")
    class MatchingStrategy {

        @Test
        @DisplayName("JSON 含 query+url → 匹配 web_search 而非 fetch_webpage（required 加分）")
        void queryMatchesWebSearchOverFetchWebpage() {
            String content = "```json\n{\"query\": \"最新政策\", \"url\": \"https://example.com\"}\n```";
            List<LlmToolResponse.ToolCall> result =
                    SpringAiLlmClient.tryParseTextToolCalls(content, buildSearchAgentToolDefinitions());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("deep_research");
        }

        @Test
        @DisplayName("JSON 仅含 url → 匹配 fetch_webpage")
        void urlOnlyMatchesFetchWebpage() {
            String content = "```json\n{\"url\": \"https://example.com\"}\n```";
            List<LlmToolResponse.ToolCall> result =
                    SpringAiLlmClient.tryParseTextToolCalls(content, buildSearchAgentToolDefinitions());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("fetch_webpage");
        }
    }
}