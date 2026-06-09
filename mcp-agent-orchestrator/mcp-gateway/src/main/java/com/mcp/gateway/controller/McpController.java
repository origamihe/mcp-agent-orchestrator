package com.mcp.gateway.controller;

import com.mcp.core.mcp.model.McpMessage;
import com.mcp.core.service.LlmConfigService;
import com.mcp.engine.orchestrator.AgentOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
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

    private final AgentOrchestrator orchestrator;
    private final LlmConfigService llmConfigService;
    private final String ollamaBaseUrl;
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public McpController(AgentOrchestrator orchestrator,
                          LlmConfigService llmConfigService,
                          @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl) {
        this.orchestrator = orchestrator;
        this.llmConfigService = llmConfigService;
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
        // TODO: 从 ToolRegistry 获取工具列表
        return Mono.just(McpMessage.builder()
                .id(req.getId())
                .result("tools will be listed here")
                .build());
    }

    private Mono<McpMessage> handleToolCall(McpMessage req) {
        // TODO: 调用 ToolExecutor
        return Mono.just(McpMessage.builder()
                .id(req.getId())
                .result("Tool call result")
                .build());
    }

    private Mono<McpMessage> handleAgentProcess(McpMessage req) {
        String task = req.getParams() != null ? req.getParams().toString() : "";
        return orchestrator.processRequest(task, req.getId())
                .map(result -> McpMessage.builder()
                        .id(req.getId())
                        .result(result)
                        .build());
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
                                            .block();
                                }
                                return result;
                            });
                });
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
                    } catch (Exception ignored) {
                    }
                    return List.<Map<String, String>>of();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }
}