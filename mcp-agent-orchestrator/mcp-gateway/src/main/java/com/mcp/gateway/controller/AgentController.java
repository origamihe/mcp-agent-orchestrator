package com.mcp.gateway.controller;

import com.mcp.engine.agent.card.AgentCard;
import com.mcp.engine.agent.registry.AgentRegistry;
import com.mcp.engine.orchestrator.MultiAgentOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Agent 管理 REST API
 * <p>
 * 提供 Agent 列表、详情、委派、流水线等管理接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentRegistry agentRegistry;
    private final MultiAgentOrchestrator multiAgentOrchestrator;

    /** 获取所有 Agent 卡片 */
    @GetMapping
    public ResponseEntity<List<AgentCard>> listAgents() {
        return ResponseEntity.ok(agentRegistry.getAllCards());
    }

    /** 获取 Agent 统计 */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        List<AgentCard> cards = agentRegistry.getAllCards();
        Map<String, Object> stats = Map.of(
                "total", agentRegistry.agentCount(),
                "types", cards.stream()
                        .map(c -> c.getAgentType().name())
                        .distinct()
                        .toList()
        );
        return ResponseEntity.ok(stats);
    }

    /** 委派任务到最佳 Agent */
    @PostMapping("/delegate")
    public Mono<ResponseEntity<Map<String, String>>> delegateTask(@RequestBody Map<String, String> body) {
        String task = body.get("task");
        if (task == null || task.isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "task 参数不能为空")));
        }

        List<String> skills = body.containsKey("skills")
                ? List.of(body.get("skills").split(","))
                : List.of();

        return multiAgentOrchestrator.delegate(task, skills)
                .map(result -> ResponseEntity.ok(Map.of("result", result)))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError()
                        .body(Map.of("error", e.getMessage()))));
    }

    /** 流水线执行 */
    @PostMapping("/pipeline")
    public Mono<ResponseEntity<Map<String, String>>> pipeline(@RequestBody Map<String, Object> body) {
        String task = (String) body.get("task");
        @SuppressWarnings("unchecked")
        List<String> agentIds = (List<String>) body.get("agentIds");

        if (task == null || agentIds == null || agentIds.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "task 和 agentIds 参数不能为空")));
        }

        return multiAgentOrchestrator.pipeline(task, agentIds)
                .map(result -> ResponseEntity.ok(Map.of("result", result)))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError()
                        .body(Map.of("error", e.getMessage()))));
    }

    /** 并行执行 */
    @PostMapping("/parallel")
    public Mono<ResponseEntity<Map<String, String>>> parallel(@RequestBody Map<String, Object> body) {
        String task = (String) body.get("task");
        @SuppressWarnings("unchecked")
        List<String> agentIds = (List<String>) body.get("agentIds");

        if (task == null || agentIds == null || agentIds.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("error", "task 和 agentIds 参数不能为空")));
        }

        return multiAgentOrchestrator.parallel(task, agentIds)
                .map(result -> ResponseEntity.ok(Map.of("result", result)))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError()
                        .body(Map.of("error", e.getMessage()))));
    }
}