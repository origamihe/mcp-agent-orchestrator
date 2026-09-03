package com.mcp.gateway.controller;

import com.mcp.core.service.RunService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/runs")
@RequiredArgsConstructor
public class RunController {

    private final RunService runService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listRuns(
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String sessionId) {
        if (agentId != null) {
            return ResponseEntity.ok(runService.getRunsByAgent(agentId));
        }
        if (sessionId != null) {
            return ResponseEntity.ok(runService.getRunsBySession(sessionId));
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getRun(@PathVariable String id) {
        return ResponseEntity.ok(runService.getRunDetail(id));
    }

    @GetMapping("/{id}/trace")
    public ResponseEntity<Map<String, Object>> getRunTrace(@PathVariable String id) {
        return ResponseEntity.ok(runService.getRunTrace(id));
    }
}