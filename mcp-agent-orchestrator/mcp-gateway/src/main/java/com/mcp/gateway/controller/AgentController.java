package com.mcp.gateway.controller;

import com.mcp.core.repository.LlmConfigRepository;
import com.mcp.core.repository.PromptTemplateRepository;
import com.mcp.core.service.ChatHistoryService;
import com.mcp.core.service.RunService;
import com.mcp.engine.agent.card.AgentCard;
import com.mcp.engine.agent.registry.AgentRegistry;
import com.mcp.engine.orchestrator.MultiAgentOrchestrator;
import com.mcp.tools.registry.ToolRegistry;
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
    private final ToolRegistry toolRegistry;
    private final ChatHistoryService chatHistoryService;
    private final RunService runService;
    private final LlmConfigRepository llmConfigRepository;
    private final PromptTemplateRepository promptTemplateRepository;

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

    /** 获取单个 Agent 详情 */
    @GetMapping("/{id}")
    public ResponseEntity<AgentCard> getAgent(@PathVariable String id) {
        return agentRegistry.getCard(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** 获取 Agent 关联的 Model 配置 */
    @GetMapping("/{id}/model")
    public ResponseEntity<?> getAgentModel(@PathVariable String id) {
        if (agentRegistry.getCard(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(llmConfigRepository.findAll());
    }

    /** 获取 Agent 关联的 Prompt 模板 */
    @GetMapping("/{id}/prompt")
    public ResponseEntity<?> getAgentPrompt(@PathVariable String id) {
        if (agentRegistry.getCard(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(promptTemplateRepository.findAll());
    }

    /** 获取 Agent 关联的 Tool 列表 */
    @GetMapping("/{id}/tools")
    public ResponseEntity<?> getAgentTools(@PathVariable String id) {
        return agentRegistry.getCard(id)
                .map(card -> {
                    if (card.getToolNames() == null || card.getToolNames().isEmpty()) {
                        return ResponseEntity.ok(toolRegistry.getAllTools());
                    }
                    var allTools = toolRegistry.getAllTools();
                    var matched = allTools.stream()
                            .filter(t -> card.getToolNames().contains(t.getName()))
                            .toList();
                    return ResponseEntity.ok(matched);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** 获取 Agent 关联的 Session 列表 */
    @GetMapping("/{id}/sessions")
    public Mono<ResponseEntity<List<Map<String, Object>>>> getAgentSessions(@PathVariable String id) {
        if (agentRegistry.getCard(id).isEmpty()) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        return chatHistoryService.getSessionsByAgentId(id)
                .map(ResponseEntity::ok);
    }

    /** 获取 Agent 关联的 Run 列表 */
    @GetMapping("/{id}/runs")
    public ResponseEntity<List<Map<String, Object>>> getAgentRuns(
            @PathVariable String id,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "20") int limit) {
        if (agentRegistry.getCard(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var runs = runService.getRunsByAgent(id);
        if (status != null) {
            runs = runs.stream()
                    .filter(r -> status.equalsIgnoreCase((String) r.get("status")))
                    .toList();
        }
        if (runs.size() > limit) {
            runs = runs.subList(0, limit);
        }
        return ResponseEntity.ok(runs);
    }

    /** 获取 Agent 配置 */
    @GetMapping("/{id}/config")
    public ResponseEntity<Map<String, Object>> getAgentConfig(@PathVariable String id) {
        return agentRegistry.getCard(id)
                .map(card -> {
                    Map<String, Object> config = new java.util.LinkedHashMap<>();
                    config.put("agentId", card.getAgentId());
                    config.put("agentName", card.getAgentName());
                    config.put("agentType", card.getAgentType().name());
                    config.put("skills", card.getSkills());
                    config.put("toolNames", card.getToolNames());
                    config.put("supportsStreaming", card.isSupportsStreaming());
                    config.put("maxConcurrentTasks", card.getMaxConcurrentTasks());
                    config.put("version", card.getVersion());
                    config.put("description", card.getDescription());
                    config.put("inputSchema", card.getInputSchema());
                    config.put("outputSchema", card.getOutputSchema());
                    return ResponseEntity.ok(config);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** 更新 Agent 配置 */
    @PutMapping("/{id}/config")
    public ResponseEntity<Map<String, Object>> updateAgentConfig(
            @PathVariable String id, @RequestBody Map<String, Object> config) {
        if (agentRegistry.getCard(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        log.info("Agent {} config updated: {}", id, config);
        return ResponseEntity.ok(config);
    }

    /** 更新 Agent Prompt */
    @PutMapping("/{id}/prompt")
    public ResponseEntity<Map<String, Object>> updateAgentPrompt(
            @PathVariable String id, @RequestBody Map<String, Object> prompt) {
        if (agentRegistry.getCard(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        log.info("Agent {} prompt updated: {}", id, prompt);
        return ResponseEntity.ok(prompt);
    }

    /** 获取 Agent 权限 */
    @GetMapping("/{id}/permissions")
    public ResponseEntity<Map<String, Object>> getAgentPermissions(@PathVariable String id) {
        return agentRegistry.getCard(id)
                .map(card -> {
                    Map<String, Object> permissions = new java.util.LinkedHashMap<>();
                    permissions.put("agentId", card.getAgentId());
                    permissions.put("allowedTools", card.getToolNames());
                    permissions.put("maxConcurrentTasks", card.getMaxConcurrentTasks());
                    return ResponseEntity.ok(permissions);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** 更新 Agent 权限 */
    @PutMapping("/{id}/permissions")
    public ResponseEntity<Map<String, Object>> updateAgentPermissions(
            @PathVariable String id, @RequestBody Map<String, Object> permissions) {
        if (agentRegistry.getCard(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        log.info("Agent {} permissions updated: {}", id, permissions);
        return ResponseEntity.ok(permissions);
    }
}