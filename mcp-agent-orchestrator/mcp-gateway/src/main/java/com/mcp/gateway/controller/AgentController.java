package com.mcp.gateway.controller;

import com.mcp.core.repository.LlmConfigRepository;
import com.mcp.core.repository.PromptTemplateRepository;
import com.mcp.core.domain.prompt.PromptType;
import com.mcp.core.entity.PromptTemplateEntity;
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
        return agentRegistry.getCard(id)
                .map(card -> {
                    String promptName = card.getPromptName();
                    if (promptName == null || promptName.isBlank()) {
                        return ResponseEntity.ok(promptTemplateRepository.findAll());
                    }
                    var prompts = promptTemplateRepository.findByName(promptName);
                    if (prompts.isEmpty()) {
                        return ResponseEntity.notFound().build();
                    }
                    return ResponseEntity.ok(prompts.get(0));
                })
                .orElse(ResponseEntity.notFound().build());
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
                    config.put("promptName", card.getPromptName());
                    config.put("modelName", card.getModelName());
                    return ResponseEntity.ok(config);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** 更新 Agent 配置 */
    @PutMapping("/{id}/config")
    public ResponseEntity<Map<String, Object>> updateAgentConfig(
            @PathVariable String id, @RequestBody Map<String, Object> config) {
        return agentRegistry.getCard(id)
                .map(card -> {
                    if (config.containsKey("agentName")) {
                        card.setAgentName((String) config.get("agentName"));
                    }
                    if (config.containsKey("description")) {
                        card.setDescription((String) config.get("description"));
                    }
                    if (config.containsKey("version")) {
                        card.setVersion((String) config.get("version"));
                    }
                    if (config.containsKey("supportsStreaming")) {
                        card.setSupportsStreaming((Boolean) config.get("supportsStreaming"));
                    }
                    if (config.containsKey("maxConcurrentTasks")) {
                        Object maxTasks = config.get("maxConcurrentTasks");
                        if (maxTasks instanceof Integer) {
                            card.setMaxConcurrentTasks((Integer) maxTasks);
                        } else if (maxTasks instanceof Number) {
                            card.setMaxConcurrentTasks(((Number) maxTasks).intValue());
                        }
                    }
                    @SuppressWarnings("unchecked")
                    List<String> skills = (List<String>) config.get("skills");
                    if (skills != null) {
                        card.setSkills(skills);
                    }
                    @SuppressWarnings("unchecked")
                    List<String> toolNames = (List<String>) config.get("toolNames");
                    if (toolNames != null) {
                        card.setToolNames(toolNames);
                    }
                    if (config.containsKey("promptName")) {
                        card.setPromptName((String) config.get("promptName"));
                    }
                    if (config.containsKey("modelName")) {
                        card.setModelName((String) config.get("modelName"));
                    }

                    log.info("Agent {} config updated: name={}, type={}, skills={}",
                            id, card.getAgentName(), card.getAgentType(), card.getSkills());

                    Map<String, Object> result = new java.util.LinkedHashMap<>();
                    result.put("agentId", card.getAgentId());
                    result.put("agentName", card.getAgentName());
                    result.put("agentType", card.getAgentType().name());
                    result.put("skills", card.getSkills());
                    result.put("toolNames", card.getToolNames());
                    result.put("supportsStreaming", card.isSupportsStreaming());
                    result.put("maxConcurrentTasks", card.getMaxConcurrentTasks());
                    result.put("version", card.getVersion());
                    result.put("description", card.getDescription());
                    result.put("promptName", card.getPromptName());
                    result.put("modelName", card.getModelName());
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** 更新 Agent Prompt */
    @PutMapping("/{id}/prompt")
    public ResponseEntity<Map<String, Object>> updateAgentPrompt(
            @PathVariable String id, @RequestBody Map<String, Object> prompt) {
        return agentRegistry.getCard(id)
                .map(card -> {
                    String promptName = card.getPromptName();
                    if (promptName == null || promptName.isBlank()) {
                        return ResponseEntity.badRequest()
                                .body(Map.<String, Object>of("error", "Agent 未配置 promptName，无法更新 Prompt"));
                    }
                    String templateText = (String) prompt.get("templateText");
                    if (templateText == null || templateText.isBlank()) {
                        return ResponseEntity.badRequest()
                                .body(Map.<String, Object>of("error", "templateText 参数不能为空"));
                    }

                    var existingPrompts = promptTemplateRepository.findByName(promptName);
                    if (!existingPrompts.isEmpty()) {
                        PromptTemplateEntity entity = existingPrompts.get(0);
                        entity.setTemplateText(templateText);
                        entity.setVersion(entity.getVersion() + 1);
                        promptTemplateRepository.save(entity);
                        log.info("Agent {} prompt updated: name={}, version={}", id, promptName, entity.getVersion());
                    } else {
                        PromptTemplateEntity entity = new PromptTemplateEntity();
                        entity.setName(promptName);
                        entity.setVariant("default");
                        entity.setVersion(1);
                        entity.setType(PromptType.AGENT_SPECIFIC);
                        entity.setTemplateText(templateText);
                        entity.setDescription("Auto-created for agent " + id);
                        entity.setWeight(1.0);
                        entity.setEnabled(true);
                        promptTemplateRepository.save(entity);
                        log.info("Agent {} prompt created: name={}", id, promptName);
                    }
                    return ResponseEntity.ok(prompt);
                })
                .orElse(ResponseEntity.notFound().build());
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
        return agentRegistry.getCard(id)
                .map(card -> {
                    @SuppressWarnings("unchecked")
                    List<String> allowedTools = (List<String>) permissions.get("allowedTools");
                    if (allowedTools != null) {
                        card.setToolNames(allowedTools);
                    }

                    Object maxTasks = permissions.get("maxConcurrentTasks");
                    if (maxTasks instanceof Integer) {
                        card.setMaxConcurrentTasks((Integer) maxTasks);
                    } else if (maxTasks instanceof Number) {
                        card.setMaxConcurrentTasks(((Number) maxTasks).intValue());
                    }

                    log.info("Agent {} permissions updated: tools={}, maxTasks={}",
                            id, card.getToolNames(), card.getMaxConcurrentTasks());

                    Map<String, Object> result = new java.util.LinkedHashMap<>();
                    result.put("agentId", card.getAgentId());
                    result.put("allowedTools", card.getToolNames());
                    result.put("maxConcurrentTasks", card.getMaxConcurrentTasks());
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}