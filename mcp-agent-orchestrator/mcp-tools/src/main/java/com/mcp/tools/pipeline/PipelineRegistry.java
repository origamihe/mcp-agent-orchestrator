package com.mcp.tools.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管道注册中心 — 管理预定义和动态注册的工具管道。
 * <p>
 * 预定义管道覆盖常见工作流模式，减少 LLM 决策轮次。
 */
@Slf4j
@Component
public class PipelineRegistry {

    private final Map<String, ToolPipeline> pipelines = new ConcurrentHashMap<>();

    public PipelineRegistry() {
        registerDefaults();
    }

    private void registerDefaults() {
        register(ToolPipeline.builder()
                .pipelineId("search-and-summarize")
                .name("搜索并汇总")
                .description("联网搜索后汇总关键信息")
                .steps(List.of(
                        ToolPipelineStep.builder()
                                .stepId("search")
                                .toolName("multi_search")
                                .arguments(Map.of("query", "${userInput}"))
                                .extractField("data")
                                .build(),
                        ToolPipelineStep.builder()
                                .stepId("fetch")
                                .toolName("fetch_webpage")
                                .arguments(Map.of("url", "{{firstUrl}}"))
                                .extractField("data")
                                .failFast(false)
                                .build()
                ))
                .build());

        register(ToolPipeline.builder()
                .pipelineId("search-and-generate-docx")
                .name("搜索并生成文档")
                .description("联网搜索后将结果整理成 Word 文档")
                .steps(List.of(
                        ToolPipelineStep.builder()
                                .stepId("search")
                                .toolName("multi_search")
                                .arguments(Map.of("query", "${userInput}"))
                                .extractField("data")
                                .build(),
                        ToolPipelineStep.builder()
                                .stepId("generate")
                                .toolName("generate_docx")
                                .arguments(Map.of(
                                        "title", "${userInput}",
                                        "content", "${search}"))
                                .build()
                ))
                .build());

        register(ToolPipeline.builder()
                .pipelineId("search-and-generate-ppt")
                .name("搜索并生成演示文稿")
                .description("联网搜索后生成 PPT 演示文稿")
                .steps(List.of(
                        ToolPipelineStep.builder()
                                .stepId("search")
                                .toolName("multi_search")
                                .arguments(Map.of("query", "${userInput}"))
                                .extractField("data")
                                .build(),
                        ToolPipelineStep.builder()
                                .stepId("generate")
                                .toolName("generate_ppt")
                                .arguments(Map.of(
                                        "title", "${userInput}",
                                        "content", "${search}"))
                                .build()
                ))
                .build());

        register(ToolPipeline.builder()
                .pipelineId("fetch-and-validate")
                .name("获取并验证网页内容")
                .description("获取网页内容后进行质量验证")
                .steps(List.of(
                        ToolPipelineStep.builder()
                                .stepId("fetch")
                                .toolName("fetch_webpage")
                                .arguments(Map.of("url", "${userInput}"))
                                .extractField("data")
                                .build(),
                        ToolPipelineStep.builder()
                                .stepId("validate")
                                .toolName("validate_content")
                                .arguments(Map.of("content", "${fetch}"))
                                .failFast(false)
                                .build()
                ))
                .build());

        log.info("[PipelineRegistry] Registered {} default pipelines: {}",
                pipelines.size(), pipelines.keySet());
    }

    public void register(ToolPipeline pipeline) {
        pipelines.put(pipeline.getPipelineId(), pipeline);
        log.info("[PipelineRegistry] Pipeline registered: id={}, name={}, steps={}",
                pipeline.getPipelineId(), pipeline.getName(), pipeline.getSteps().size());
    }

    public void unregister(String pipelineId) {
        pipelines.remove(pipelineId);
        log.info("[PipelineRegistry] Pipeline unregistered: {}", pipelineId);
    }

    public ToolPipeline get(String pipelineId) {
        return pipelines.get(pipelineId);
    }

    public List<ToolPipeline> listAll() {
        return List.copyOf(pipelines.values());
    }

    public boolean contains(String pipelineId) {
        return pipelines.containsKey(pipelineId);
    }
}