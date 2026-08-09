package com.mcp.engine.trace;

import com.mcp.core.context.BuildContext;
import com.mcp.core.context.ContextAssembler;
import com.mcp.core.context.PromptContextBuilder;
import com.mcp.core.context.provider.HostContextProvider;
import com.mcp.core.context.provider.IdentityContextProvider;
import com.mcp.core.context.provider.RelationshipContextProvider;
import com.mcp.core.context.provider.WorkspaceContextProvider;
import com.mcp.engine.runtime.AgentRuntime;
import com.mcp.llm.client.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TraceCollector - 运行时追踪收集")
class TraceCollectorTest {

    @Mock
    private LlmClient llmClient;

    private AgentRuntime agentRuntime;
    private TraceCollector.InMemory collector;

    @BeforeEach
    void setUp() {
        IdentityContextProvider identityProvider = new IdentityContextProvider();
        RelationshipContextProvider relationshipProvider = new RelationshipContextProvider();
        WorkspaceContextProvider workspaceProvider = new WorkspaceContextProvider();
        HostContextProvider hostProvider = new HostContextProvider();

        List<com.mcp.core.context.ContextProvider> providers = List.of(
                identityProvider, relationshipProvider, workspaceProvider, hostProvider);
        PromptContextBuilder promptContextBuilder = new PromptContextBuilder(providers);
        ContextAssembler contextAssembler = new ContextAssembler();

        agentRuntime = new AgentRuntime(promptContextBuilder, contextAssembler, llmClient);

        collector = new TraceCollector.InMemory();
        agentRuntime.setTraceCollector(collector);
    }

    @Nested
    @DisplayName("Trace 记录生成")
    class TraceRecordGeneration {

        @Test
        @DisplayName("trace001: assemble() 应生成 TraceRecord")
        void shouldGenerateTraceOnAssemble() {
            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("你是一个有用的助手")
                    .userMessage("帮我整理昨天讨论的 Java 项目")
                    .workspacePrompt("项目: java-project")
                    .build();

            agentRuntime.assemble(ctx);

            assertThat(collector.size()).isEqualTo(1);
            TraceRecord trace = collector.getLatest();

            assertThat(trace).isNotNull();
            assertThat(trace.traceId()).isNotNull();
            assertThat(trace.userMessage()).isEqualTo("帮我整理昨天讨论的 Java 项目");
            assertThat(trace.renderedPrompt()).isNotEmpty();
            assertThat(trace.elapsedMs()).isGreaterThanOrEqualTo(0);
            assertThat(trace.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("trace002: 多次调用应生成多条 TraceRecord")
        void shouldGenerateMultipleTraces() {
            BuildContext ctx1 = BuildContext.builder()
                    .baseSystemPrompt("BASE")
                    .userMessage("请求1")
                    .build();
            BuildContext ctx2 = BuildContext.builder()
                    .baseSystemPrompt("BASE")
                    .userMessage("请求2")
                    .build();

            agentRuntime.assemble(ctx1);
            agentRuntime.assemble(ctx2);

            assertThat(collector.size()).isEqualTo(2);
            assertThat(collector.getRecords().get(0).userMessage()).isEqualTo("请求1");
            assertThat(collector.getRecords().get(1).userMessage()).isEqualTo("请求2");
        }

        @Test
        @DisplayName("trace003: TraceRecord 应包含 layerCount")
        void shouldContainLayerCount() {
            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("你是一个有用的助手")
                    .userMessage("你好")
                    .workspacePrompt("test-project")
                    .build();

            agentRuntime.assemble(ctx);

            TraceRecord trace = collector.getLatest();
            assertThat(trace.layerCount()).isGreaterThan(0);
        }

        @Test
        @DisplayName("trace004: TraceRecord 应包含 workspaceState")
        void shouldContainWorkspaceState() {
            String workspaceContent = "项目: my-project\n文件: Main.java, Config.java";

            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("BASE")
                    .userMessage("test")
                    .workspacePrompt(workspaceContent)
                    .build();

            agentRuntime.assemble(ctx);

            TraceRecord trace = collector.getLatest();
            assertThat(trace.workspaceState()).isEqualTo(workspaceContent);
        }

        @Test
        @DisplayName("trace005: TraceRecord 应包含 systemPrompt（截断到500字符）")
        void shouldTruncateSystemPrompt() {
            String longPrompt = "BASE".repeat(300);

            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt(longPrompt)
                    .userMessage("test")
                    .build();

            agentRuntime.assemble(ctx);

            TraceRecord trace = collector.getLatest();
            assertThat(trace.systemPrompt().length()).isLessThanOrEqualTo(503);
        }

        @Test
        @DisplayName("trace006: 每次调用的 traceId 应唯一")
        void shouldHaveUniqueTraceId() {
            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("BASE")
                    .userMessage("test")
                    .build();

            agentRuntime.assemble(ctx);
            agentRuntime.assemble(ctx);

            TraceRecord t1 = collector.getRecords().get(0);
            TraceRecord t2 = collector.getRecords().get(1);

            assertThat(t1.traceId()).isNotEqualTo(t2.traceId());
        }

        @Test
        @DisplayName("trace007: elapsedMs 应在合理范围内")
        void shouldHaveReasonableElapsedTime() {
            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("BASE")
                    .userMessage("test")
                    .build();

            agentRuntime.assemble(ctx);

            TraceRecord trace = collector.getLatest();
            assertThat(trace.elapsedMs()).isLessThan(5000);
        }
    }

    @Nested
    @DisplayName("TraceCollector 行为")
    class TraceCollectorBehavior {

        @Test
        @DisplayName("trace008: NOOP 实现不应抛出异常")
        void noopShouldNotThrow() {
            TraceCollector noop = TraceCollector.NOOP;
            TraceRecord record = TraceRecord.builder()
                    .userMessage("test")
                    .build();
            noop.record(record);
        }

        @Test
        @DisplayName("trace009: InMemory 应限制最大容量")
        void inMemoryShouldLimitSize() {
            TraceCollector.InMemory smallCollector = new TraceCollector.InMemory(3);
            for (int i = 0; i < 5; i++) {
                smallCollector.record(TraceRecord.builder().userMessage("msg" + i).build());
            }

            assertThat(smallCollector.size()).isEqualTo(3);
            assertThat(smallCollector.getRecords().get(0).userMessage()).isEqualTo("msg2");
            assertThat(smallCollector.getRecords().get(2).userMessage()).isEqualTo("msg4");
        }

        @Test
        @DisplayName("trace010: 未设置 TraceCollector 时默认 NOOP 不抛异常")
        void defaultNoopShouldNotThrow() {
            IdentityContextProvider identityProvider = new IdentityContextProvider();
            List<com.mcp.core.context.ContextProvider> providers = List.of(identityProvider);
            PromptContextBuilder builder = new PromptContextBuilder(providers);
            ContextAssembler assembler = new ContextAssembler();

            AgentRuntime runtime = new AgentRuntime(builder, assembler, llmClient);

            BuildContext ctx = BuildContext.builder()
                    .baseSystemPrompt("BASE")
                    .userMessage("test")
                    .build();

            runtime.assemble(ctx);
        }
    }
}