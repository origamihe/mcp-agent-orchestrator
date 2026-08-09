package com.mcp.engine.sanitizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResponseSanitizer - 回复清洗器")
class ResponseSanitizerTest {

    private final ResponseSanitizer sanitizer = new ResponseSanitizer();

    @Nested
    @DisplayName("Internal_Memory_Storage 清洗")
    class InternalMemoryStorageSanitization {

        @Test
        @DisplayName("应移除 [Internal_Memory_Storage] 块，保留自然语言回复")
        void shouldRemoveInternalMemoryStorageBlockAndKeepNaturalReply() {
            String raw = """
                    [Internal_Memory_Storage]
                    {"key": "UserNickname", "value": "叉烧"}
                                        
                    知道了，以后我就叫你叉烧。""";

            String cleaned = sanitizer.sanitize(raw);

            assertThat(cleaned).isEqualTo("知道了，以后我就叫你叉烧。");
        }

        @Test
        @DisplayName("应移除单行格式的 [Internal_Memory_Storage]")
        void shouldRemoveSingleLineInternalMemoryStorage() {
            String raw = "[Internal_Memory_Storage]{\"key\":\"UserNickname\",\"value\":\"叉烧\"}\n\n知道了。";

            String cleaned = sanitizer.sanitize(raw);

            assertThat(cleaned).isEqualTo("知道了。");
        }

        @Test
        @DisplayName("纯自然语言回复应保持不变")
        void shouldNotModifyPureNaturalLanguageReply() {
            String raw = "好的，我记住了，你喜欢 Java。";

            String cleaned = sanitizer.sanitize(raw);

            assertThat(cleaned).isEqualTo(raw);
        }

        @Test
        @DisplayName("null 输入应返回 null")
        void shouldReturnNullForNullInput() {
            assertThat(sanitizer.sanitize(null)).isNull();
        }

        @Test
        @DisplayName("空字符串应返回空字符串")
        void shouldReturnEmptyForEmptyInput() {
            assertThat(sanitizer.sanitize("")).isEmpty();
            assertThat(sanitizer.sanitize("   ")).isEqualTo("   ");
        }

        @Test
        @DisplayName("应移除多个 [Internal_Memory_Storage] 块")
        void shouldRemoveMultipleInternalMemoryStorageBlocks() {
            String raw = """
                    [Internal_Memory_Storage]
                    {"key": "nickname", "value": "叉烧"}
                                        
                    [Internal_Memory_Storage]
                    {"key": "preference", "value": "Java"}
                                        
                    好的，都记住了。""";

            String cleaned = sanitizer.sanitize(raw);

            assertThat(cleaned).isEqualTo("好的，都记住了。");
        }
    }

    @Nested
    @DisplayName("Tool_Call 清洗（预留扩展）")
    class ToolCallSanitization {

        @Test
        @DisplayName("应移除 [Tool_Call] 块")
        void shouldRemoveToolCallBlock() {
            String raw = """
                    [Tool_Call]
                    {"name": "search", "args": {"query": "天气"}}
                                        
                    今天天气不错。""";

            String cleaned = sanitizer.sanitize(raw);

            assertThat(cleaned).isEqualTo("今天天气不错。");
        }

        @Test
        @DisplayName("应移除多行嵌套 JSON 的 [Tool_Call] 块")
        void shouldRemoveMultiLineNestedToolCall() {
            String raw = """
                    [Tool_Call]
                    {
                      "name": "search",
                      "args": {
                        "query": "天气",
                        "limit": 5
                      }
                    }
                                        
                    查询结果如下。""";

            String cleaned = sanitizer.sanitize(raw);

            assertThat(cleaned).isEqualTo("查询结果如下。");
        }

        @Test
        @DisplayName("应移除单行 [Tool_Call] 块")
        void shouldRemoveSingleLineToolCall() {
            String raw = "[Tool_Call]{\"name\":\"calc\",\"args\":{\"a\":1,\"b\":2}}\n\n答案是3。";

            String cleaned = sanitizer.sanitize(raw);

            assertThat(cleaned).isEqualTo("答案是3。");
        }

        @Test
        @DisplayName("应移除多个 [Tool_Call] 块")
        void shouldRemoveMultipleToolCallBlocks() {
            String raw = """
                    [Tool_Call]
                    {"name": "search", "args": {"query": "A"}}
                                        
                    [Tool_Call]
                    {"name": "search", "args": {"query": "B"}}
                                        
                    综合结果。""";

            String cleaned = sanitizer.sanitize(raw);

            assertThat(cleaned).isEqualTo("综合结果。");
        }
    }

    @Nested
    @DisplayName("混合场景")
    class MixedScenarios {

        @Test
        @DisplayName("应同时移除 [Internal_Memory_Storage] 和 [Tool_Call] 块")
        void shouldRemoveBothMemoryAndToolCallBlocks() {
            String raw = """
                    [Internal_Memory_Storage]
                    {"key": "nickname", "value": "叉烧"}
                                        
                    [Tool_Call]
                    {"name": "search", "args": {"query": "天气"}}
                                        
                    好的，搜索完成。""";

            String cleaned = sanitizer.sanitize(raw);

            assertThat(cleaned).isEqualTo("好的，搜索完成。");
        }

        @Test
        @DisplayName("工具块穿插在自然语言中应被正确移除")
        void shouldRemoveToolBlocksInterleavedWithNaturalLanguage() {
            String raw = """
                    你好！
                    [Internal_Memory_Storage]
                    {"key": "greeting", "value": "已问候"}
                    今天有什么可以帮你的？""";

            String cleaned = sanitizer.sanitize(raw);

            assertThat(cleaned).isEqualTo("你好！\n今天有什么可以帮你的？");
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("只有工具块没有自然语言时应返回空字符串")
        void shouldReturnEmptyWhenOnlyToolBlocks() {
            String raw = """
                    [Internal_Memory_Storage]
                    {"key": "nickname", "value": "叉烧"}""";

            String cleaned = sanitizer.sanitize(raw);

            assertThat(cleaned).isEmpty();
        }

        @Test
        @DisplayName("不区分大小写匹配")
        void shouldBeCaseInsensitive() {
            String raw = "[internal_memory_storage]{\"key\":\"x\",\"value\":\"y\"}\n\n你好。";

            String cleaned = sanitizer.sanitize(raw);

            assertThat(cleaned).isEqualTo("你好。");
        }
    }
}