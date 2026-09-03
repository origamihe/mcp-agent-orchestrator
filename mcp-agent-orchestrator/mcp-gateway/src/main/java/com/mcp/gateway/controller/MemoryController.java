package com.mcp.gateway.controller;

import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.repository.MemoryPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryPackageRepository memoryPackageRepository;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listMemory(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "50") int limit) {
        List<MemoryPackageEntity> memories;
        if (sessionId != null) {
            memories = memoryPackageRepository.findBySessionIdOrderByWeightDesc(sessionId);
        } else if (userId != null) {
            memories = memoryPackageRepository.findByUserIdOrderByWeightDesc(userId);
        } else {
            memories = memoryPackageRepository.findAll();
        }
        if (memories.size() > limit) {
            memories = memories.subList(0, limit);
        }
        return ResponseEntity.ok(memories.stream().map(this::toMemoryMap).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getMemory(@PathVariable Long id) {
        return memoryPackageRepository.findById(id)
                .map(this::toMemoryMap)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createMemory(@RequestBody Map<String, Object> body) {
        MemoryPackageEntity entity = new MemoryPackageEntity();
        entity.setSessionId((String) body.getOrDefault("sessionId", ""));
        entity.setUserId((String) body.getOrDefault("userId", ""));
        entity.setContent((String) body.getOrDefault("content", ""));
        entity.setImportance(body.containsKey("importance") ? ((Number) body.get("importance")).intValue() : 50);
        if (body.containsKey("metadata")) {
            entity.setMetadata(body.get("metadata").toString());
        }
        entity.setCreatedAt(LocalDateTime.now());
        entity.setLastAccessedAt(LocalDateTime.now());
        memoryPackageRepository.save(entity);
        return ResponseEntity.ok(toMemoryMap(entity));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMemory(@PathVariable Long id) {
        if (!memoryPackageRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        memoryPackageRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchMemory(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "20") int limit) {
        List<MemoryPackageEntity> memories;
        if (sessionId != null) {
            memories = memoryPackageRepository.findBySessionIdOrderByWeightDesc(sessionId);
        } else {
            memories = memoryPackageRepository.findAll();
        }
        if (query != null && !query.isBlank()) {
            String lowerQuery = query.toLowerCase();
            memories = memories.stream()
                    .filter(m -> m.getContent() != null && m.getContent().toLowerCase().contains(lowerQuery))
                    .collect(Collectors.toList());
        }
        if (memories.size() > limit) {
            memories = memories.subList(0, limit);
        }
        return ResponseEntity.ok(memories.stream().map(this::toMemoryMap).collect(Collectors.toList()));
    }

    private Map<String, Object> toMemoryMap(MemoryPackageEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("type", entity.getMemoryType() != null ? entity.getMemoryType().name() : "FACT");
        map.put("content", entity.getContent());
        map.put("importance", entity.getImportance());
        map.put("sessionId", entity.getSessionId());
        map.put("createdAt", entity.getCreatedAt());
        map.put("updatedAt", entity.getLastAccessedAt());
        return map;
    }
}