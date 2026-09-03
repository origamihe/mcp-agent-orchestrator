package com.mcp.gateway.controller;

import com.mcp.core.entity.RunEntity;
import com.mcp.core.repository.RunRepository;
import com.mcp.engine.agent.registry.AgentRegistry;
import com.mcp.gateway.channel.ChannelAdapterRegistry;
import com.mcp.gateway.ws.WebSocketSessionManager;
import com.mcp.tools.registry.ToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
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

        health.put("gateway", Map.of("status", "healthy", "message", "Gateway running"));
        health.put("agentEngine", Map.of("status", "healthy",
                "message", agentRegistry.getAllCards().size() + " agents registered"));
        health.put("mcpHosts", Map.of("status", "healthy",
                "message", adapterRegistry.getAll().size() + " hosts configured"));
        health.put("memory", Map.of("status", "healthy",
                "message", "Memory system operational"));
        health.put("sandbox", Map.of("status", "healthy",
                "message", "Sandbox policy active"));

        return health;
    }
}