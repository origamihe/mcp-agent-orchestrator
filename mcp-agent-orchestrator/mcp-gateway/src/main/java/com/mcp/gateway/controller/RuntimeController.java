package com.mcp.gateway.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/runtime")
@RequiredArgsConstructor
public class RuntimeController {

    private final Environment environment;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("timestamp", System.currentTimeMillis());

        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("heapUsed", runtime.totalMemory() - runtime.freeMemory());
        memory.put("heapMax", runtime.maxMemory());
        memory.put("heapUsedPercent", Math.round((1.0 - (double) runtime.freeMemory() / runtime.totalMemory()) * 100));
        result.put("memory", memory);

        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        result.put("uptime", Duration.ofMillis(runtimeBean.getUptime()).toString()
                .replace("PT", "").replace("H", "h ").replace("M", "m ").replace("S", "s").trim());

        result.put("javaVersion", runtimeBean.getVmVersion());
        result.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        result.put("activeProfile", String.join(",", environment.getActiveProfiles()));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("serverPort", environment.getProperty("server.port", "8080"));
        config.put("activeProfile", String.join(",", environment.getActiveProfiles()));
        config.put("springAppName", environment.getProperty("spring.application.name", "mcp-agent-orchestrator"));
        return ResponseEntity.ok(config);
    }
}