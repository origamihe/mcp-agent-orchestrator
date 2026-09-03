package com.mcp.gateway.controller;

import com.mcp.core.entity.WorkspaceEntity;
import com.mcp.core.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceRepository workspaceRepository;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listWorkspaces() {
        List<Map<String, Object>> workspaces = workspaceRepository.findAll().stream()
                .map(this::toWorkspaceMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(workspaces);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getWorkspace(@PathVariable String id) {
        return workspaceRepository.findByWorkspaceId(id)
                .map(this::toWorkspaceMap)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateWorkspace(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return workspaceRepository.findByWorkspaceId(id)
                .map(entity -> {
                    if (body.containsKey("name")) entity.setName((String) body.get("name"));
                    if (body.containsKey("projectPath")) entity.setProjectPath((String) body.get("projectPath"));
                    if (body.containsKey("projectRoot")) entity.setProjectRoot((String) body.get("projectRoot"));
                    workspaceRepository.save(entity);
                    return ResponseEntity.ok(toWorkspaceMap(entity));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toWorkspaceMap(WorkspaceEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getWorkspaceId());
        map.put("name", entity.getName());
        map.put("projectPath", entity.getProjectPath());
        map.put("projectRoot", entity.getProjectRoot());
        map.put("lastActiveFile", entity.getLastActiveFile());
        map.put("lastActiveLine", entity.getLastActiveLine());
        map.put("lastActiveAt", entity.getLastActiveAt());
        map.put("createdAt", entity.getCreatedAt());
        map.put("updatedAt", entity.getUpdatedAt());
        return map;
    }
}