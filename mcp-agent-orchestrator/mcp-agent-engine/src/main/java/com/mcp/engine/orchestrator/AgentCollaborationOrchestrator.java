package com.mcp.engine.orchestrator;

import com.mcp.engine.agent.Agent;
import com.mcp.engine.agent.LLMRequest;
import com.mcp.engine.agent.bus.A2aMessageBus;
import com.mcp.engine.agent.card.A2aProtocol;
import com.mcp.engine.agent.card.AgentCard;
import com.mcp.engine.agent.registry.AgentRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Agent 协作编排器 — 实现实际的多 Agent 链式协作场景。
 *
 * 提供预定义的协作流水线：
 * <ul>
 *   <li>Search → Code → Chat: 搜索 → 代码生成 → 最终合成</li>
 *   <li>Code → Review → Chat: 代码生成 → 审查 → 最终反馈</li>
 *   <li>Search → Chat: 搜索 → 总结回答</li>
 * </ul>
 *
 * 基础 PIPELINE / PARALLEL / DELEGATE 模式参见 {@link MultiAgentOrchestrator}。
 */
@Slf4j
@Component
public class AgentCollaborationOrchestrator {

    private final AgentRegistry agentRegistry;
    private final A2aMessageBus messageBus;

    public AgentCollaborationOrchestrator(AgentRegistry agentRegistry, A2aMessageBus messageBus) {
        this.agentRegistry = agentRegistry;
        this.messageBus = messageBus;
    }

    /**
     * Search → Code → Chat 流水线。
     * 适用场景：用户要求「根据最新信息，帮我写一个 XXX 脚本」
     */
    public Mono<String> searchToCodeToChat(String userMessage) {
        log.info("[Collab] Search→Code→Chat pipeline start");
        return executeSearchPhase(userMessage)
                .flatMap(searchResult -> executeCodePhase(searchResult, userMessage))
                .flatMap(codeResult -> executeSynthesisPhase(codeResult, userMessage))
                .doOnSuccess(result -> log.info("[Collab] Search→Code→Chat pipeline complete"))
                .doOnError(e -> log.error("[Collab] Search→Code→Chat pipeline failed: {}", e.getMessage()));
    }

    /**
     * Code → Review → Chat 流水线。
     * 适用场景：用户要求「帮我写一段代码，然后审查一下」
     */
    public Mono<String> codeToReviewToChat(String userMessage) {
        log.info("[Collab] Code→Review→Chat pipeline start");
        return executeCodePhase(userMessage, userMessage)
                .flatMap(codeResult -> executeReviewPhase(codeResult, userMessage))
                .flatMap(reviewResult -> executeSynthesisPhase(reviewResult, userMessage))
                .doOnSuccess(result -> log.info("[Collab] Code→Review→Chat pipeline complete"))
                .doOnError(e -> log.error("[Collab] Code→Review→Chat pipeline failed: {}", e.getMessage()));
    }

    /**
     * Search → Chat 流水线。
     * 适用场景：用户要求「搜索 XXX 并总结」
     */
    public Mono<String> searchToChat(String userMessage) {
        log.info("[Collab] Search→Chat pipeline start");
        return executeSearchPhase(userMessage)
                .flatMap(searchResult -> executeSynthesisPhase(searchResult, userMessage))
                .doOnSuccess(result -> log.info("[Collab] Search→Chat pipeline complete"))
                .doOnError(e -> log.error("[Collab] Search→Chat pipeline failed: {}", e.getMessage()));
    }

    /**
     * 通过 A2A 消息总线委派任务到指定 Agent。
     */
    public Mono<String> a2aDelegation(String fromAgentId, String toAgentId, String task, String context) {
        log.info("[Collab] A2A delegation: {} -> {} (task: {})", fromAgentId, toAgentId,
                task.length() > 50 ? task.substring(0, 50) + "..." : task);

        A2aProtocol.AgentTaskRequest request = A2aProtocol.AgentTaskRequest.builder()
                .fromAgentId(fromAgentId)
                .toAgentId(toAgentId)
                .task(task)
                .context(context)
                .build();

        return messageBus.sendTask(request)
                .map(response -> {
                    if (response.isSuccess()) {
                        log.info("[Collab] A2A task {} completed by {}", response.getTaskId(), toAgentId);
                        return response.getResult();
                    } else {
                        log.warn("[Collab] A2A task {} failed: {}", response.getTaskId(), response.getErrorMessage());
                        return "[A2A 委派失败] " + (response.getErrorMessage() != null ? response.getErrorMessage() : "未知错误");
                    }
                });
    }

    public List<AgentCard> getAvailableAgents() {
        return agentRegistry.getAllCards();
    }

    private Mono<String> executeSearchPhase(String userMessage) {
        Agent searchAgent = agentRegistry.getAgent("search-agent").orElse(null);
        if (searchAgent == null) {
            log.warn("[Collab] SearchAgent not available, using original message");
            return Mono.just(userMessage);
        }
        LLMRequest request = LLMRequest.of(
                "你是一个搜索专家。请根据用户需求搜索相关信息，整理成结构化的搜索结果。",
                "请搜索以下内容的相关信息：\n" + userMessage + "\n\n请以结构化方式返回搜索结果，包含关键事实和数据来源。"
        );
        log.info("[Collab] Phase 1: SearchAgent executing...");
        return searchAgent.execute(request)
                .doOnSuccess(r -> log.info("[Collab] Phase 1: SearchAgent completed ({} chars)", r != null ? r.length() : 0));
    }

    private Mono<String> executeCodePhase(String searchResult, String userMessage) {
        Agent codeAgent = agentRegistry.getAgent("code-agent").orElse(null);
        if (codeAgent == null) {
            log.warn("[Collab] CodeAgent not available, using search result");
            return Mono.just(searchResult);
        }
        LLMRequest request = LLMRequest.of(
                "你是一个代码专家。请根据搜索结果中的信息，生成相应的代码实现。",
                "搜索结果：\n" + searchResult + "\n\n用户需求：\n" + userMessage + "\n\n请根据以上信息生成代码，包含必要的注释。"
        );
        log.info("[Collab] Phase 2: CodeAgent executing...");
        return codeAgent.execute(request)
                .doOnSuccess(r -> log.info("[Collab] Phase 2: CodeAgent completed ({} chars)", r != null ? r.length() : 0));
    }

    private Mono<String> executeReviewPhase(String codeResult, String userMessage) {
        Agent codeAgent = agentRegistry.getAgent("code-agent").orElse(null);
        if (codeAgent == null) {
            log.warn("[Collab] CodeAgent not available for review phase");
            return Mono.just(codeResult);
        }
        LLMRequest request = LLMRequest.of(
                "你是一个代码审查专家。请仔细审查以下代码，指出潜在问题并给出改进建议。",
                "原始需求：\n" + userMessage + "\n\n代码实现：\n" + codeResult +
                        "\n\n请审查以上代码，重点关注：安全性、性能、可维护性、错误处理。"
        );
        log.info("[Collab] Phase 2: CodeAgent reviewing...");
        return codeAgent.execute(request)
                .doOnSuccess(r -> log.info("[Collab] Phase 2: CodeAgent review completed ({} chars)", r != null ? r.length() : 0));
    }

    private Mono<String> executeSynthesisPhase(String previousResult, String userMessage) {
        Agent chatAgent = agentRegistry.getAgent("chat-agent").orElse(null);
        if (chatAgent == null) {
            log.warn("[Collab] ChatAgent not available, using previous result");
            return Mono.just(previousResult);
        }
        LLMRequest request = LLMRequest.of(
                "你是一个智能助手，需要将前面的分析结果整理成用户友好的最终回答。请保持回答简洁、清晰、有帮助。",
                "用户原始问题：\n" + userMessage + "\n\n前面的分析结果：\n" + previousResult +
                        "\n\n请将以上结果整理成给用户的最终回答。"
        );
        log.info("[Collab] Phase 3: ChatAgent synthesizing...");
        return chatAgent.execute(request)
                .doOnSuccess(r -> log.info("[Collab] Phase 3: ChatAgent synthesis completed ({} chars)", r != null ? r.length() : 0));
    }
}