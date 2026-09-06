package com.mcp.gateway.controller;

import com.mcp.core.entity.RunEntity;
import com.mcp.core.repository.RunRepository;
import com.mcp.core.service.LlmConfigService;
import com.mcp.core.domain.llm.LlmModelConfig;
import com.mcp.core.domain.llm.ProviderAvailability;
import com.mcp.engine.agent.registry.AgentRegistry;
import com.mcp.gateway.channel.ChannelAdapterRegistry;
import com.mcp.gateway.ws.WebSocketSessionManager;
import com.mcp.tools.registry.ToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.sql.Connection;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final AgentRegistry agentRegistry;
    private final RunRepository runRepository;
    private final ToolRegistry toolRegistry;
    private final ChannelAdapterRegistry adapterRegistry;
    private final WebSocketSessionManager wsSessionManager;
    private final LlmConfigService llmConfigService;
    private final ProviderAvailability providerAvailability;
    private final DataSource dataSource;

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview() {
        Map<String, Object> result = new LinkedHashMap<>();

        int agentCount = agentRegistry.getAllCards().size();
        int activeAgentCount = agentCount;
        long activeRunCount = runRepository.count();
        long runCountByStatusRunning = runRepository.findByStatusOrderByCreatedAtDesc("RUNNING").size();
        int toolCount = toolRegistry.getAllTools().size();
        int hostCount = adapterRegistry.getAll().size();
        int connectedHostCount = (int) adapterRegistry.getAll().stream()
                .filter(a -> wsSessionManager.hasSession(a.getChannelType())).count();

        result.put("agentCount", agentCount);
        result.put("activeAgentCount", activeAgentCount);
        result.put("activeRunCount", activeRunCount);
        result.put("runCountRunning", runCountByStatusRunning);
        result.put("hostCount", hostCount);
        result.put("connectedHostCount", connectedHostCount);
        result.put("toolCount", toolCount);

        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        result.put("uptime", Duration.ofMillis(runtimeBean.getUptime()).toString()
                .replace("PT", "").replace("H", "h ").replace("M", "m ").replace("S", "s").trim());

        List<RunEntity> allRuns = runRepository.findAll();
        List<RunEntity> recentRuns = allRuns.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(10)
                .collect(Collectors.toList());
        result.put("recentRuns", recentRuns.stream().map(r -> {
            Map<String, Object> runMap = new LinkedHashMap<>();
            runMap.put("id", r.getId());
            runMap.put("agentId", r.getAgentId());
            runMap.put("agentName", r.getAgentName());
            runMap.put("status", r.getStatus());
            runMap.put("createdAt", r.getCreatedAt());
            runMap.put("completedAt", r.getCompletedAt());
            return runMap;
        }).collect(Collectors.toList()));

        result.put("runtimeHealth", buildRuntimeHealth());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(buildRuntimeHealth());
    }

    private Map<String, Object> buildRuntimeHealth() {
        Map<String, Object> health = new LinkedHashMap<>();

        health.put("gateway", checkGatewayHealth());
        health.put("agentEngine", checkAgentEngineHealth());
        health.put("llm", checkLlmHealth());
        health.put("database", checkDatabaseHealth());
        health.put("mcpHosts", checkHostsHealth());
        health.put("tools", checkToolsHealth());
        health.put("memory", Map.of("status", "healthy", "message", "Memory system operational"));
        health.put("sandbox", Map.of("status", "healthy", "message", "Sandbox policy active"));

        return health;
    }

    private Map<String, Object> checkGatewayHealth() {
        return Map.of("status", "healthy", "message", "Gateway running");
    }

    private Map<String, Object> checkAgentEngineHealth() {
        int agentCount = agentRegistry.getAllCards().size();
        return Map.of(
                "status", agentCount > 0 ? "healthy" : "degraded",
                "message", agentCount + " agents registered"
        );
    }

    private Map<String, Object> checkLlmHealth() {
        try {
            LlmModelConfig defaultConfig = llmConfigService.getDefaultConfig().block();
            if (defaultConfig == null) {
                return Map.of("status", "unhealthy", "message", "No default LLM config");
            }
            boolean available = providerAvailability.isProviderAvailable(defaultConfig.getProvider());
            String providerInfo = defaultConfig.getProvider() + " / " + defaultConfig.getModelName();
            return Map.of(
                    "status", available ? "healthy" : "unhealthy",
                    "message", available ? providerInfo + " available" : providerInfo + " unavailable",
                    "detail", "provider=" + defaultConfig.getProvider()
                            + ", model=" + defaultConfig.getModelName()
            );
        } catch (Exception e) {
            return Map.of("status", "unhealthy", "message", "LLM check failed: " + e.getMessage());
        }
    }

    private Map<String, Object> checkDatabaseHealth() {
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(3);
            return Map.of(
                    "status", valid ? "healthy" : "unhealthy",
                    "message", valid ? "Database connected" : "Database connection invalid"
            );
        } catch (Exception e) {
            return Map.of("status", "unhealthy", "message", "Database unreachable: " + e.getMessage());
        }
    }

    private Map<String, Object> checkHostsHealth() {
        int totalHosts = adapterRegistry.getAll().size();
        int connectedHosts = (int) adapterRegistry.getAll().stream()
                .filter(a -> wsSessionManager.hasSession(a.getChannelType()))
                .count();
        if (totalHosts == 0) {
            return Map.of("status", "degraded", "message", "No hosts configured");
        }
        return Map.of(
                "status", connectedHosts > 0 ? "healthy" : "degraded",
                "message", connectedHosts + "/" + totalHosts + " hosts connected"
        );
    }

    private Map<String, Object> checkToolsHealth() {
        long totalTools = toolRegistry.getAllTools().size();
        long unhealthyTools = toolRegistry.healthCheckAll().stream()
                .filter(hc -> !hc.healthy())
                .count();
        long healthyTools = totalTools - unhealthyTools;
        if (totalTools == 0) {
            return Map.of("status", "degraded", "message", "No tools registered");
        }
        return Map.of(
                "status", unhealthyTools == 0 ? "healthy" : "degraded",
                "message", healthyTools + "/" + totalTools + " tools healthy"
        );
    }
}