package com.mcp.gateway.controller;

import com.mcp.common.tool.ToolRiskLevel;
import com.mcp.gateway.host.capability.CapabilityRiskRegistry;
import com.mcp.tools.sandbox.SandboxPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final CapabilityRiskRegistry riskRegistry;
    private final SandboxPolicy sandboxPolicy;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listPolicies() {
        List<Map<String, Object>> policies = riskRegistry.getAllEntries().entrySet().stream()
                .map(entry -> toPolicyMap(entry.getKey(), entry.getValue()))
                .toList();
        return ResponseEntity.ok(policies);
    }

    @GetMapping("/{capability}")
    public ResponseEntity<Map<String, Object>> getPolicy(@PathVariable String capability) {
        ToolRiskLevel level = riskRegistry.getRiskLevel(capability);
        return ResponseEntity.ok(toPolicyMap(capability, level));
    }

    @PutMapping("/{capability}")
    public ResponseEntity<Map<String, Object>> updatePolicy(
            @PathVariable String capability,
            @RequestBody Map<String, String> body) {
        String riskLevel = body.get("riskLevel");
        if (riskLevel == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "riskLevel is required"));
        }
        try {
            ToolRiskLevel level = ToolRiskLevel.valueOf(riskLevel.toUpperCase());
            riskRegistry.updateRiskLevel(capability, level);
            return ResponseEntity.ok(toPolicyMap(capability, level));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid risk level: " + riskLevel,
                    "valid", List.of(ToolRiskLevel.values())
            ));
        }
    }

    private Map<String, Object> toPolicyMap(String capability, ToolRiskLevel riskLevel) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("capability", capability);
        map.put("riskLevel", riskLevel.name());
        SandboxPolicy.Decision decision = sandboxPolicy.decide(riskLevel);
        map.put("sandboxType", decision.name());
        map.put("sandboxEnabled", decision != SandboxPolicy.Decision.NONE);
        map.put("blocked", decision == SandboxPolicy.Decision.BLOCKED);
        return map;
    }
}