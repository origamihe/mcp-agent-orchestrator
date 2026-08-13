package com.mcp.engine.orchestrator;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 协作流水线注册中心 — 管理所有已注册的协作流水线定义。
 *
 * 新增流水线只需调用 {@link #register(CollaborationPipeline)} 或在此类中定义，
 * 无需修改 {@link AgentCollaborationOrchestrator}。
 *
 * 扩展规则：新增流水线 = 1 个文件（仅此类或外部配置类），无需修改 Orchestrator。
 */
@Slf4j
@Component
public class CollaborationPipelineRegistry {

    private final Map<String, CollaborationPipeline> pipelines = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        register(searchToCodeToChat());
        register(codeToReviewToChat());
        register(searchToChat());
        log.info("[PipelineRegistry] 已注册 {} 条协作流水线: {}", pipelines.size(), pipelines.keySet());
    }

    public void register(CollaborationPipeline pipeline) {
        if (pipelines.containsKey(pipeline.name())) {
            log.warn("[PipelineRegistry] 流水线 '{}' 已存在，将被覆盖", pipeline.name());
        }
        pipelines.put(pipeline.name(), pipeline);
        log.info("[PipelineRegistry] 注册流水线: {} ({} 阶段)", pipeline.name(), pipeline.stageCount());
    }

    public Optional<CollaborationPipeline> get(String name) {
        return Optional.ofNullable(pipelines.get(name));
    }

    public List<CollaborationPipeline> getAll() {
        return List.copyOf(pipelines.values());
    }

    public int pipelineCount() {
        return pipelines.size();
    }

    public boolean exists(String name) {
        return pipelines.containsKey(name);
    }

    private static CollaborationPipeline searchToCodeToChat() {
        return new CollaborationPipeline(
                "searchToCodeToChat",
                "Search → Code → Chat：搜索最新信息 → 代码生成 → 最终合成",
                List.of(
                        new CollaborationPipelineStage(
                                "search-agent",
                                "你是一个搜索专家。请根据用户需求搜索相关信息，整理成结构化的搜索结果。",
                                "请搜索以下内容的相关信息：\n{userMessage}\n\n请以结构化方式返回搜索结果，包含关键事实和数据来源。"
                        ),
                        new CollaborationPipelineStage(
                                "code-agent",
                                "你是一个代码专家。请根据搜索结果中的信息，生成相应的代码实现。",
                                "搜索结果：\n{input}\n\n用户需求：\n{userMessage}\n\n请根据以上信息生成代码，包含必要的注释。"
                        ),
                        new CollaborationPipelineStage(
                                "chat-agent",
                                "你是一个智能助手，需要将前面的分析结果整理成用户友好的最终回答。请保持回答简洁、清晰、有帮助。",
                                "用户原始问题：\n{userMessage}\n\n前面的分析结果：\n{input}\n\n请将以上结果整理成给用户的最终回答。"
                        )
                )
        );
    }

    private static CollaborationPipeline codeToReviewToChat() {
        return new CollaborationPipeline(
                "codeToReviewToChat",
                "Code → Review → Chat：代码生成 → 审查 → 最终反馈",
                List.of(
                        new CollaborationPipelineStage(
                                "code-agent",
                                "你是一个代码专家。请根据用户需求生成高质量的代码实现。",
                                "请根据以下需求生成代码：\n{userMessage}\n\n请生成完整可运行的代码，包含必要的注释。"
                        ),
                        new CollaborationPipelineStage(
                                "chat-agent",
                                "你是一个代码审查专家。请仔细审查以下代码，指出潜在问题并给出改进建议。",
                                "原始需求：\n{userMessage}\n\n代码实现：\n{input}\n\n请审查以上代码，重点关注：安全性、性能、可维护性、错误处理。"
                        ),
                        new CollaborationPipelineStage(
                                "chat-agent",
                                "你是一个智能助手，需要将前面的分析结果整理成用户友好的最终回答。请保持回答简洁、清晰、有帮助。",
                                "用户原始问题：\n{userMessage}\n\n前面的分析结果：\n{input}\n\n请将以上结果整理成给用户的最终回答。"
                        )
                )
        );
    }

    private static CollaborationPipeline searchToChat() {
        return new CollaborationPipeline(
                "searchToChat",
                "Search → Chat：搜索 → 总结回答",
                List.of(
                        new CollaborationPipelineStage(
                                "search-agent",
                                "你是一个搜索专家。请根据用户需求搜索相关信息，整理成结构化的搜索结果。",
                                "请搜索以下内容的相关信息：\n{userMessage}\n\n请以结构化方式返回搜索结果，包含关键事实和数据来源。"
                        ),
                        new CollaborationPipelineStage(
                                "chat-agent",
                                "你是一个智能助手，需要将搜索结果整理成用户友好的最终回答。请保持回答简洁、清晰、有帮助。",
                                "用户原始问题：\n{userMessage}\n\n搜索结果：\n{input}\n\n请将以上搜索结果总结成给用户的最终回答。"
                        )
                )
        );
    }
}