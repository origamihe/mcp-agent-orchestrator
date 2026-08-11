package com.mcp.gateway.controller;

import com.mcp.core.mcp.model.McpMessage;
import com.mcp.core.service.LlmConfigService;
import com.mcp.engine.orchestrator.AgentOrchestrator;
import com.mcp.tools.executor.ToolExecutor;
import com.mcp.tools.model.ToolDefinition;
import com.mcp.tools.model.ToolExecutionRequest;
import com.mcp.tools.registry.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/mcp")
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);

    private final AgentOrchestrator orchestrator;
    private final LlmConfigService llmConfigService;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final String ollamaBaseUrl;
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public McpController(AgentOrchestrator orchestrator,
                          LlmConfigService llmConfigService,
                          ToolRegistry toolRegistry,
                          ToolExecutor toolExecutor,
                          @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl) {
        this.orchestrator = orchestrator;
        this.llmConfigService = llmConfigService;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.ollamaBaseUrl = ollamaBaseUrl;
    }

    /**
     * MCP 标准入口（JSON-RPC 风格）
     */
    @PostMapping
    public Mono<McpMessage> handleMcpRequest(@RequestBody McpMessage message) {
        return processMcpMessage(message);
    }

    private Mono<McpMessage> processMcpMessage(McpMessage req) {
        return switch (req.getMethod()) {
            case "initialize" -> handleInitialize(req);
            case "tools/list" -> handleListTools(req);
            case "tools/call" -> handleToolCall(req);
            case "agent/process" -> handleAgentProcess(req);
            default -> Mono.just(McpMessage.builder()
                    .id(req.getId())
                    .error(new com.mcp.core.mcp.model.McpError(-32601, "Method not found", null))
                    .build());
        };
    }

    private Mono<McpMessage> handleInitialize(McpMessage req) {
        return Mono.just(McpMessage.builder()
                .id(req.getId())
                .result("MCP Agent Orchestrator v0.0.1 initialized")
                .build());
    }

    private Mono<McpMessage> handleListTools(McpMessage req) {
        List<ToolDefinition> tools = toolRegistry.getAllTools();
        List<Map<String, Object>> toolList = tools.stream()
                .map(tool -> {
                    Map<String, Object> toolMap = new LinkedHashMap<>();
                    toolMap.put("name", tool.getName());
                    toolMap.put("description", tool.getDescription());
                    toolMap.put("inputSchema", tool.getInputSchema() != null ? tool.getInputSchema() : Map.of());
                    return toolMap;
                })
                .collect(Collectors.toList());
        return Mono.just(McpMessage.builder()
                .id(req.getId())
                .result(Map.of("tools", toolList))
                .build());
    }

    private Mono<McpMessage> handleToolCall(McpMessage req) {
        Object params = req.getParams();
        if (params == null) {
            return Mono.just(McpMessage.builder()
                    .id(req.getId())
                    .error(new com.mcp.core.mcp.model.McpError(-32602, "Missing params", null))
                    .build());
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> paramsMap = (params instanceof Map)
                    ? (Map<String, Object>) params
                    : new ObjectMapper().convertValue(params, Map.class);

            String toolName = (String) paramsMap.get("name");
            if (toolName == null || toolName.isEmpty()) {
                return Mono.just(McpMessage.builder()
                        .id(req.getId())
                        .error(new com.mcp.core.mcp.model.McpError(-32602, "Missing tool name", null))
                        .build());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) paramsMap.getOrDefault("arguments", Map.of());

            ToolExecutionRequest toolRequest = new ToolExecutionRequest();
            toolRequest.setToolName(toolName);
            toolRequest.setArguments(new HashMap<>(arguments));
            toolRequest.setRequestId(req.getId());

            return toolExecutor.execute(toolRequest)
                    .map(result -> {
                        Map<String, Object> content = new LinkedHashMap<>();
                        content.put("type", "text");
                        content.put("text", result != null ? result.toString() : "");
                        return McpMessage.builder()
                                .id(req.getId())
                                .result(Map.of("content", List.of(content)))
                                .build();
                    })
                    .onErrorResume(error -> Mono.just(McpMessage.builder()
                            .id(req.getId())
                            .error(new com.mcp.core.mcp.model.McpError(-32000, "Tool execution failed: " + error.getMessage(), null))
                            .build()));
        } catch (Exception e) {
            return Mono.just(McpMessage.builder()
                    .id(req.getId())
                    .error(new com.mcp.core.mcp.model.McpError(-32603, "Invalid params: " + e.getMessage(), null))
                    .build());
        }
    }

    private Mono<McpMessage> handleAgentProcess(McpMessage req) {
        String task = req.getParams() != null ? req.getParams().toString() : "";
        return orchestrator.processRequestWithSystemPrompt(task, req.getId(), null, null)
                .map(result -> McpMessage.builder()
                        .id(req.getId())
                        .result(result)
                        .build());
    }

    @GetMapping("/workspaces")
    public Mono<ResponseEntity<List<Map<String, Object>>>> getWorkspaces() {
        List<Map<String, Object>> workspaces = new ArrayList<>();
        Map<String, Object> defaultWs = new LinkedHashMap<>();
        defaultWs.put("workspaceId", "default");
        defaultWs.put("name", "Default Workspace");
        defaultWs.put("projectPath", System.getProperty("user.dir"));
        workspaces.add(defaultWs);
        return Mono.just(ResponseEntity.ok(workspaces));
    }

    @GetMapping("/configs")
    public Mono<?> getAvailableLlmConfigs() {
        return llmConfigService.getAllEnabledConfigs()
                .flatMap(dbConfigs -> {
                    List<Map<String, String>> result = dbConfigs.stream()
                            .map(c -> Map.of(
                                    "configId", c.getConfigId(),
                                    "provider", c.getProvider().getCode(),
                                    "modelName", c.getModelName()
                            ))
                            .collect(Collectors.toList());

                    Set<String> existingNames = result.stream()
                            .map(m -> m.get("modelName"))
                            .collect(Collectors.toSet());

                    return discoverOllamaModels()
                            .filter(ollamaModel -> !existingNames.contains(ollamaModel.get("modelName")))
                            .collectList()
                            .map(ollamaModels -> {
                                result.addAll(ollamaModels);
                                if (result.isEmpty()) {
                                    return llmConfigService.getDefaultConfig()
                                            .map(defaultCfg -> List.of(Map.of(
                                                    "configId", defaultCfg.getConfigId(),
                                                    "provider", defaultCfg.getProvider().getCode(),
                                                    "modelName", defaultCfg.getModelName()
                                            )))
                                            .defaultIfEmpty(result);
                                }
                                return Mono.just(result);
                            });
                })
                .flatMap(Mono::just);
    }

    private Flux<Map<String, String>> discoverOllamaModels() {
        return Mono.fromCallable(() -> {
                    try {
                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create(ollamaBaseUrl + "/api/tags"))
                                .GET()
                                .timeout(Duration.ofSeconds(5))
                                .build();
                        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                        if (resp.statusCode() == 200) {
                            ObjectMapper mapper = new ObjectMapper();
                            var root = mapper.readTree(resp.body());
                            var models = root.get("models");
                            List<Map<String, String>> result = new ArrayList<>();
                            if (models != null && models.isArray()) {
                                for (var node : models) {
                                    String name = node.get("name").asText();
                                    result.add(Map.of(
                                            "configId", "ollama-" + name.replace(":", "-"),
                                            "provider", "ollama",
                                            "modelName", name
                                    ));
                                }
                            }
                            return result;
                        }
                    } catch (Exception e) {
                        log.warn("Failed to discover Ollama models from {}: {}", ollamaBaseUrl, e.getMessage());
                    }
                    return List.<Map<String, String>>of();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }
}