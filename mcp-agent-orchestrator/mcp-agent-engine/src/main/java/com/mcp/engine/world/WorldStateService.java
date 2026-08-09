package com.mcp.engine.world;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.common.channel.WorldState;
import com.mcp.core.entity.WorldStateEntity;
import com.mcp.core.repository.WorldStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 世界状态服务 — 负责世界状态的持久化加载与保存。
 * 在会话启动时从 DB 加载世界状态，在会话更新时保存回 DB。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorldStateService {

    private final WorldStateRepository repository;
    private final ObjectMapper objectMapper;

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {};

    /**
     * 根据会话 ID 加载世界状态。
     * 如果 DB 中不存在，返回空 WorldState。
     */
    @Transactional(readOnly = true)
    public WorldState loadBySessionId(String sessionId) {
        return repository.findBySessionId(sessionId)
                .map(this::toWorldState)
                .orElseGet(() -> {
                    log.debug("[WorldState] No persisted state for session {}, using empty", sessionId);
                    return new WorldState();
                });
    }

    /**
     * 保存世界状态到 DB（upsert：存在则更新，不存在则插入）。
     */
    @Transactional
    public void save(String sessionId, WorldState worldState) {
        if (worldState == null) return;

        WorldStateEntity entity = repository.findBySessionId(sessionId)
                .orElseGet(() -> {
                    WorldStateEntity newEntity = new WorldStateEntity();
                    newEntity.setSessionId(sessionId);
                    return newEntity;
                });

        entity.setCurrentTime(worldState.getCurrentTime());
        entity.setCurrentLocation(worldState.getCurrentLocation());
        entity.setWeather(worldState.getWeather());
        entity.setAtmosphere(worldState.getAtmosphere());
        entity.setNpcs(toJson(worldState.getPresentNpcs()));
        entity.setActiveEvents(toJson(worldState.getActiveEvents()));
        entity.setWorldRules(toJson(worldState.getWorldRules()));
        entity.setRecentHappenings(toJson(worldState.getRecentHappenings()));

        repository.save(entity);
        log.debug("[WorldState] Saved world state for session {}", sessionId);
    }

    /**
     * 删除指定会话的世界状态。
     */
    @Transactional
    public void deleteBySessionId(String sessionId) {
        repository.deleteBySessionId(sessionId);
        log.info("[WorldState] Deleted world state for session {}", sessionId);
    }

    private WorldState toWorldState(WorldStateEntity entity) {
        WorldState ws = new WorldState();
        ws.setCurrentTime(entity.getCurrentTime());
        ws.setCurrentLocation(entity.getCurrentLocation());
        ws.setWeather(entity.getWeather());
        ws.setAtmosphere(entity.getAtmosphere());
        ws.setPresentNpcs(fromJsonList(entity.getNpcs()));
        ws.setActiveEvents(fromJsonList(entity.getActiveEvents()));
        ws.setWorldRules(fromJsonMap(entity.getWorldRules()));
        ws.setRecentHappenings(fromJsonList(entity.getRecentHappenings()));
        return ws;
    }

    private List<String> fromJsonList(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("[WorldState] Failed to parse JSON list: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private Map<String, String> fromJsonMap(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, STRING_MAP_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("[WorldState] Failed to parse JSON map: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return "[]";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("[WorldState] Failed to serialize to JSON: {}", e.getMessage());
            return obj instanceof Map ? "{}" : "[]";
        }
    }
}