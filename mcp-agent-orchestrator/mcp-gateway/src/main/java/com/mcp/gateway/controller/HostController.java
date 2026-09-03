package com.mcp.gateway.controller;

import com.mcp.gateway.channel.ChannelAdapter;
import com.mcp.gateway.channel.ChannelAdapterRegistry;
import com.mcp.gateway.host.capability.CapabilityRiskRegistry;
import com.mcp.gateway.ws.WebSocketSessionManager;
import com.mcp.common.tool.ToolRiskLevel;
import com.mcp.tools.sandbox.SandboxPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/hosts")
@RequiredArgsConstructor
public class HostController {

    private final ChannelAdapterRegistry adapterRegistry;
    private final WebSocketSessionManager wsSessionManager;
    private final CapabilityRiskRegistry riskRegistry;
    private final SandboxPolicy sandboxPolicy;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listHosts() {
        List<Map<String, Object>> hosts = adapterRegistry.getAll().stream()
                .map(this::toHostMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(hosts);
    }

    @GetMapping("/{channelType}")
    public ResponseEntity<Map<String, Object>> getHost(@PathVariable String channelType) {
        return adapterRegistry.get(channelType)
                .map(this::toHostMap)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/capabilities")
    public ResponseEntity<List<Map<String, Object>>> listCapabilities() {
        List<Map<String, Object>> capabilities = riskRegistry.getAllEntries().entrySet().stream()
                .map(entry -> {
                    Map<String, Object> cap = new LinkedHashMap<>();
                    cap.put("name", entry.getKey());
                    cap.put("riskLevel", entry.getValue().name());
                    SandboxPolicy.Decision decision = sandboxPolicy.decide(entry.getValue());
                    cap.put("enabled", decision != SandboxPolicy.Decision.BLOCKED);
                    cap.put("description", decision.name());
                    return cap;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(capabilities);
    }

    private Map<String, Object> toHostMap(ChannelAdapter adapter) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", adapter.getChannelType());
        map.put("channelType", adapter.getChannelType());
        map.put("name", adapter.getChannelType());
        map.put("enabled", adapter.isEnabled());
        map.put("connected", wsSessionManager.hasSession(adapter.getChannelType()));
        map.put("status", adapter.getStatus());
        map.put("capabilities", riskRegistry.getAllEntries().entrySet().stream()
                .map(entry -> {
                    Map<String, Object> cap = new LinkedHashMap<>();
                    cap.put("name", entry.getKey());
                    cap.put("description", "");
                    cap.put("enabled", true);
                    cap.put("riskLevel", entry.getValue().name());
                    return cap;
                })
                .collect(Collectors.toList()));
        map.put("projects", List.of());
        return map;
    }
}