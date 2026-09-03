package com.mcp.gateway.controller;

import com.mcp.core.entity.TraceEventEntity;
import com.mcp.core.repository.TraceEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final TraceEventRepository traceEventRepository;

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @GetMapping
    public ResponseEntity<Map<String, Object>> listLogs(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String runId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        LocalDateTime start = parseTime(startTime);
        LocalDateTime end = parseTime(endTime);
        Set<String> mappedEventTypes = mapLevelToEventTypes(level);

        Page<TraceEventEntity> page = traceEventRepository.findByFilters(
                eventType, agentId, sessionId, runId, start, end, search,
                PageRequest.of(offset / limit, limit));

        List<TraceEventEntity> filtered = page.getContent().stream()
                .filter(e -> mappedEventTypes.isEmpty() || (e.getEventType() != null
                        && mappedEventTypes.contains(e.getEventType().toUpperCase())))
                .filter(e -> module == null || module.isEmpty()
                        || (e.getOperation() != null && e.getOperation().toLowerCase().contains(module.toLowerCase())))
                .collect(Collectors.toList());

        List<Map<String, Object>> items = filtered.stream()
                .map(this::toLogMap)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("totalCount", page.getTotalElements());
        result.put("page", offset / limit);
        result.put("pageSize", limit);
        result.put("hasMore", (offset + limit) < page.getTotalElements());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        List<TraceEventEntity> all = traceEventRepository.findAll();
        Map<String, Long> levelCounts = all.stream()
                .collect(Collectors.groupingBy(
                        e -> mapEventTypeToLevel(e.getEventType()),
                        Collectors.counting()));
        Map<String, Long> moduleCounts = all.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getOperation() != null ? e.getOperation() : "unknown",
                        Collectors.counting()));
        List<Map<String, Object>> recentErrors = all.stream()
                .filter(e -> "ERROR".equalsIgnoreCase(e.getEventType())
                        || "FAILURE".equalsIgnoreCase(e.getEventType()))
                .sorted((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
                .limit(10)
                .map(this::toLogMap)
                .collect(Collectors.toList());

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCount", all.size());
        stats.put("levelCounts", levelCounts);
        stats.put("moduleCounts", moduleCounts);
        stats.put("recentErrors", recentErrors);
        return ResponseEntity.ok(stats);
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
        map.put("agentId", entity.getAgentId());
        map.put("sessionId", entity.getSessionId());
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

    private Set<String> mapLevelToEventTypes(String level) {
        if (level == null || level.isEmpty()) return Set.of();
        return switch (level.toLowerCase()) {
            case "error" -> Set.of("ERROR", "FAILURE");
            case "warn" -> Set.of("WARNING", "TIMEOUT");
            case "audit" -> Set.of("AUDIT", "SECURITY");
            case "debug" -> Set.of("START", "END", "COMPLETE");
            default -> Set.of();
        };
    }

    private LocalDateTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return null;
        try {
            return LocalDateTime.parse(timeStr, ISO_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }
}