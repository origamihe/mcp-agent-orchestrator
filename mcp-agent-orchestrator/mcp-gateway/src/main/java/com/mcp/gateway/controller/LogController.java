package com.mcp.gateway.controller;

import com.mcp.core.entity.TraceEventEntity;
import com.mcp.core.repository.TraceEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final TraceEventRepository traceEventRepository;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listLogs(
            @RequestParam(required = false) String runId,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<TraceEventEntity> events;
        if (runId != null) {
            events = traceEventRepository.findByRunIdOrderBySequenceAsc(runId);
        } else {
            events = traceEventRepository.findAll(PageRequest.of(offset / limit, limit)).getContent();
        }
        if (eventType != null) {
            events = events.stream()
                    .filter(e -> eventType.equalsIgnoreCase(e.getEventType()))
                    .collect(Collectors.toList());
        }
        if (events.size() > limit) {
            events = events.subList(0, limit);
        }
        return ResponseEntity.ok(events.stream().map(this::toLogMap).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getLog(@PathVariable Long id) {
        return traceEventRepository.findById(id)
                .map(this::toLogMap)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toLogMap(TraceEventEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("level", mapEventTypeToLevel(entity.getEventType()));
        map.put("module", entity.getOperation());
        map.put("message", entity.getOperation() + " - " + entity.getStatus());
        map.put("runId", entity.getRunId());
        map.put("metadata", entity.getMetadata());
        map.put("timestamp", entity.getStartTime());
        return map;
    }

    private String mapEventTypeToLevel(String eventType) {
        if (eventType == null) return "info";
        return switch (eventType.toUpperCase()) {
            case "ERROR", "FAILURE" -> "error";
            case "WARNING", "TIMEOUT" -> "warn";
            case "AUDIT", "SECURITY" -> "audit";
            case "START", "END", "COMPLETE" -> "debug";
            default -> "info";
        };
    }
}