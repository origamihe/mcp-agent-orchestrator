package com.mcp.core.service;

import com.mcp.core.domain.run.Run;
import com.mcp.core.domain.run.RunStatus;
import com.mcp.core.entity.RunEntity;
import com.mcp.core.entity.TraceEventEntity;
import com.mcp.core.repository.RunRepository;
import com.mcp.core.repository.TraceEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RunService {

    private final RunRepository runRepository;
    private final TraceEventRepository traceEventRepository;

    public Run createRun(String agentId, String agentName, String sessionId, String intent) {
        RunEntity entity = new RunEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setAgentId(agentId);
        entity.setAgentName(agentName);
        entity.setSessionId(sessionId);
        entity.setIntent(intent);
        entity.setStatus(RunStatus.PENDING.name());
        runRepository.save(entity);
        return toDomain(entity);
    }

    @Transactional
    public void startRun(String runId) {
        runRepository.findById(runId).ifPresent(entity -> {
            entity.setStatus(RunStatus.RUNNING.name());
            runRepository.save(entity);
        });
    }

    @Transactional
    public void completeRun(String runId, int promptTokens, int completionTokens) {
        runRepository.findById(runId).ifPresent(entity -> {
            entity.setStatus(RunStatus.COMPLETED.name());
            entity.setCompletedAt(LocalDateTime.now());
            entity.setPromptTokens(promptTokens);
            entity.setCompletionTokens(completionTokens);
            entity.setTotalTokens(promptTokens + completionTokens);
            if (entity.getCreatedAt() != null) {
                entity.setDurationMs(Duration.between(entity.getCreatedAt(), LocalDateTime.now()).toMillis());
            }
            runRepository.save(entity);
        });
    }

    @Transactional
    public void failRun(String runId) {
        runRepository.findById(runId).ifPresent(entity -> {
            entity.setStatus(RunStatus.FAILED.name());
            entity.setCompletedAt(LocalDateTime.now());
            if (entity.getCreatedAt() != null) {
                entity.setDurationMs(Duration.between(entity.getCreatedAt(), LocalDateTime.now()).toMillis());
            }
            runRepository.save(entity);
        });
    }

    @Transactional
    public void incrementToolCall(String runId) {
        runRepository.findById(runId).ifPresent(entity -> {
            entity.setToolCallCount(entity.getToolCallCount() + 1);
            runRepository.save(entity);
        });
    }

    public List<Map<String, Object>> getRunsBySession(String sessionId) {
        return runRepository.findBySessionIdOrderByCreatedAtDesc(sessionId).stream()
                .map(this::toSummaryMap)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getRunsByAgent(String agentId) {
        return runRepository.findByAgentIdOrderByCreatedAtDesc(agentId).stream()
                .map(this::toSummaryMap)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getRunDetail(String runId) {
        RunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new RuntimeException("Run not found: " + runId));
        Map<String, Object> detail = toSummaryMap(run);
        List<TraceEventEntity> traceEvents = traceEventRepository.findByRunIdOrderBySequenceAsc(runId);
        detail.put("trace", traceEvents.stream().map(this::toTraceMap).collect(Collectors.toList()));
        return detail;
    }

    public Map<String, Object> getRunTrace(String runId) {
        List<TraceEventEntity> traceEvents = traceEventRepository.findByRunIdOrderBySequenceAsc(runId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", runId);
        result.put("events", traceEvents.stream().map(this::toTraceMap).collect(Collectors.toList()));
        return result;
    }

    private Map<String, Object> toSummaryMap(RunEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("agentId", entity.getAgentId());
        map.put("agentName", entity.getAgentName());
        map.put("sessionId", entity.getSessionId());
        map.put("intent", entity.getIntent());
        map.put("status", entity.getStatus());
        map.put("duration", entity.getDurationMs());
        map.put("toolCallCount", entity.getToolCallCount());
        map.put("tokenUsage", Map.of(
                "promptTokens", entity.getPromptTokens(),
                "completionTokens", entity.getCompletionTokens(),
                "totalTokens", entity.getTotalTokens()
        ));
        map.put("createdAt", entity.getCreatedAt());
        map.put("completedAt", entity.getCompletedAt());
        return map;
    }

    private Map<String, Object> toTraceMap(TraceEventEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("parentId", entity.getParentId());
        map.put("operation", entity.getOperation());
        map.put("eventType", entity.getEventType());
        map.put("status", entity.getStatus());
        map.put("startTime", entity.getStartTime());
        map.put("endTime", entity.getEndTime());
        map.put("duration", entity.getDurationMs());
        map.put("sequence", entity.getSequence());
        map.put("metadata", entity.getMetadata());
        return map;
    }

    private Run toDomain(RunEntity entity) {
        Run run = new Run(entity.getId(), entity.getAgentId(), entity.getSessionId());
        run.setAgentName(entity.getAgentName());
        run.setIntent(entity.getIntent());
        run.setDurationMs(entity.getDurationMs());
        return run;
    }
}