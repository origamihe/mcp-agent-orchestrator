package com.mcp.gateway.controller;

import com.mcp.core.entity.LlmConfigEntity;
import com.mcp.core.repository.LlmConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController {

    private final LlmConfigRepository llmConfigRepository;

    @GetMapping("/configs")
    public ResponseEntity<List<Map<String, Object>>> listConfigs() {
        List<Map<String, Object>> configs = llmConfigRepository.findAll().stream()
                .map(this::toConfigMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(configs);
    }

    @GetMapping("/configs/{id}")
    public ResponseEntity<Map<String, Object>> getConfig(@PathVariable String id) {
        return llmConfigRepository.findById(id)
                .map(this::toConfigMap)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toConfigMap(LlmConfigEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("configId", entity.getConfigId());
        map.put("provider", entity.getProvider() != null ? entity.getProvider().name() : null);
        map.put("modelName", entity.getModelName());
        map.put("temperature", entity.getTemperature());
        map.put("maxTokens", entity.getMaxTokens());
        map.put("enabled", entity.getEnabled());
        return map;
    }
}