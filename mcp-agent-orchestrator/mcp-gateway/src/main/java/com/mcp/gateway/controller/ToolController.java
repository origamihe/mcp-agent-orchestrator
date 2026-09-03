package com.mcp.gateway.controller;

import com.mcp.tools.model.ToolDefinition;
import com.mcp.tools.model.ToolStats;
import com.mcp.tools.registry.ToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolRegistry toolRegistry;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listTools() {
        List<Map<String, Object>> tools = toolRegistry.getAllTools().stream()
                .map(this::toToolMap)
                .toList();
        return ResponseEntity.ok(tools);
    }

    @GetMapping("/{name}")
    public Mono<ResponseEntity<Map<String, Object>>> getTool(@PathVariable String name) {
        return toolRegistry.getTool(name)
                .map(this::toToolMap)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/capabilities")
    public ResponseEntity<Set<String>> listCapabilities() {
        Set<String> capabilities = toolRegistry.getAllCapabilities().stream()
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toSet());
        return ResponseEntity.ok(capabilities);
    }

    @GetMapping("/stats")
    public ResponseEntity<List<ToolStats>> getStats() {
        return ResponseEntity.ok(toolRegistry.getAllToolStats());
    }

    @PostMapping("/{name}/enable")
    public ResponseEntity<Map<String, Object>> enableTool(@PathVariable String name) {
        if (!toolRegistry.containsTool(name)) {
            return ResponseEntity.notFound().build();
        }
        toolRegistry.enableTool(name);
        return ResponseEntity.ok(Map.of("name", name, "enabled", true));
    }

    @PostMapping("/{name}/disable")
    public ResponseEntity<Map<String, Object>> disableTool(@PathVariable String name) {
        if (!toolRegistry.containsTool(name)) {
            return ResponseEntity.notFound().build();
        }
        toolRegistry.disableTool(name);
        return ResponseEntity.ok(Map.of("name", name, "enabled", false));
    }

    @GetMapping("/{name}/risk")
    public Mono<ResponseEntity<Map<String, Object>>> getToolRisk(@PathVariable String name) {
        return toolRegistry.getTool(name)
                .map(tool -> {
                    Map<String, Object> risk = new LinkedHashMap<>();
                    String riskLevel = tool.getCapabilities() != null && !tool.getCapabilities().isEmpty()
                            ? tool.getCapabilities().iterator().next().name() : "L0";
                    risk.put("riskLevel", riskLevel);
                    risk.put("assessment", "Risk level: " + riskLevel + " for tool " + tool.getName());
                    return ResponseEntity.ok(risk);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toToolMap(ToolDefinition tool) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", tool.getName());
        map.put("description", tool.getDescription());
        map.put("category", tool.getCategory() != null ? tool.getCategory().name() : null);
        map.put("capabilities", tool.getCapabilities() != null
                ? tool.getCapabilities().stream().map(Enum::name).toList()
                : List.of());
        map.put("owner", tool.getOwner() != null ? tool.getOwner().name() : null);
        map.put("enabled", tool.isEnabled());
        map.put("version", tool.getVersion());
        return map;
    }
}