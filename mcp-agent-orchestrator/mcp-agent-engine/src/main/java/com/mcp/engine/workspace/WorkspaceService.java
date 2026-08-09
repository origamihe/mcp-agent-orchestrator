package com.mcp.engine.workspace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.common.artifact.Artifact;
import com.mcp.common.workspace.Workspace;
import com.mcp.core.entity.WorkspaceEntity;
import com.mcp.core.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 工作空间服务 — 负责 Workspace 的持久化加载与保存。
 * Agent 启动时通过 workspaceId 恢复完整的工作上下文，
 * 每次交互后更新 Workspace 快照。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository repository;
    private final ObjectMapper objectMapper;

    private static final TypeReference<List<Workspace.Task>> TASK_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Workspace.Todo>> TODO_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Workspace.GitState> GIT_STATE_TYPE = new TypeReference<>() {};
    private static final TypeReference<Workspace.TerminalState> TERMINAL_STATE_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Workspace.OpenedFile>> OPENED_FILES_MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<Workspace.OpenedFile> OPENED_FILE_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Artifact>> ARTIFACT_LIST_TYPE = new TypeReference<>() {};

    /**
     * 根据 workspaceId 加载 Workspace。
     */
    @Transactional(readOnly = true)
    public Workspace loadByWorkspaceId(String workspaceId) {
        return repository.findByWorkspaceId(workspaceId)
                .map(this::toWorkspace)
                .orElseGet(() -> {
                    log.debug("[Workspace] No persisted workspace for {}, creating empty", workspaceId);
                    Workspace ws = new Workspace();
                    ws.setWorkspaceId(workspaceId);
                    return ws;
                });
    }

    /**
     * 查找最近活跃的 Workspace（用于"继续昨天那个项目"等场景）。
     */
    @Transactional(readOnly = true)
    public Optional<Workspace> findMostRecent() {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        return repository.findRecentlyActive(since)
                .stream()
                .findFirst()
                .map(this::toWorkspace);
    }

    /**
     * 根据项目路径查找 Workspace。
     */
    @Transactional(readOnly = true)
    public Optional<Workspace> findByProjectPath(String projectPath) {
        return repository.findByProjectPath(projectPath)
                .stream()
                .findFirst()
                .map(this::toWorkspace);
    }

    /**
     * 保存 Workspace 到 DB（upsert）。
     * 使用 Dirty Flag 避免无变化的 UPDATE：只有标记为 dirty 的 workspace 才会触发 DB 写入。
     */
    @Transactional
    public void save(Workspace workspace) {
        if (workspace == null || workspace.getWorkspaceId() == null) return;

        if (!workspace.isDirty()) {
            log.debug("[Workspace] Skipped save (not dirty) workspace {}", workspace.getWorkspaceId());
            return;
        }

        WorkspaceEntity entity = repository.findByWorkspaceId(workspace.getWorkspaceId())
                .orElseGet(() -> {
                    WorkspaceEntity newEntity = new WorkspaceEntity();
                    newEntity.setWorkspaceId(workspace.getWorkspaceId());
                    return newEntity;
                });

        entity.setName(workspace.getName());
        entity.setProjectPath(workspace.getProjectPath());
        entity.setProjectRoot(workspace.getProjectRoot());
        entity.setActiveTasks(toJson(workspace.getActiveTasks()));
        entity.setTodos(toJson(workspace.getTodos()));
        entity.setGitState(toJson(workspace.getGitState()));
        entity.setTerminalState(toJson(workspace.getTerminalState()));
        entity.setFileTreeSnapshot(toJson(workspace.getFileTreeSnapshot()));
        entity.setHostContexts(toJson(workspace.getHostContexts()));
        entity.setOpenedFiles(toJson(workspace.getOpenedFiles()));
        entity.setArtifacts(toJson(workspace.getArtifacts()));
        entity.setLastActiveFile(workspace.getLastActiveFile());
        entity.setLastActiveLine(workspace.getLastActiveLine());
        entity.setLastOpenedFile(toJson(workspace.getLastOpenedFile().orElse(null)));
        entity.setLastActiveAt(toLocalDateTime(workspace.getLastActiveAt()));

        repository.save(entity);
        workspace.markClean();
        log.debug("[Workspace] Saved workspace {}", workspace.getWorkspaceId());
    }

    /**
     * 更新指定 Host 的上下文快照。
     */
    @Transactional
    public void updateHostContext(String workspaceId, String hostType, String hostContextJson) {
        repository.findByWorkspaceId(workspaceId).ifPresent(entity -> {
            Map<String, String> hostContexts = fromJsonMap(entity.getHostContexts());
            if (hostContexts == null) hostContexts = new LinkedHashMap<>();
            hostContexts.put(hostType, hostContextJson);
            entity.setHostContexts(toJson(hostContexts));
            entity.setLastActiveAt(LocalDateTime.now());
            repository.save(entity);
            log.debug("[Workspace] Updated host context for {} in workspace {}", hostType, workspaceId);
        });
    }

    /**
     * 删除指定 Workspace。
     */
    @Transactional
    public void deleteByWorkspaceId(String workspaceId) {
        repository.deleteByWorkspaceId(workspaceId);
        log.info("[Workspace] Deleted workspace {}", workspaceId);
    }

    /**
     * 打开文件并追踪到 Workspace。
     */
    @Transactional
    public void openFile(String workspaceId, String path, String content, String encoding, Instant mtime, long size) {
        Workspace ws = loadByWorkspaceId(workspaceId);
        ws.openFile(path, content, encoding, mtime, size);
        save(ws);
        log.info("[Workspace] Opened file: {} in workspace {}", path, workspaceId);
    }

    /**
     * 追踪 README 文件。
     */
    @Transactional
    public void trackReadme(String workspaceId, String path, String content) {
        Workspace ws = loadByWorkspaceId(workspaceId);
        ws.trackReadme(path, content);
        save(ws);
        log.info("[Workspace] Tracked README: {} in workspace {}", path, workspaceId);
    }

    /**
     * 设置项目根目录。
     */
    @Transactional
    public void setProjectRoot(String workspaceId, String projectRoot) {
        Workspace ws = loadByWorkspaceId(workspaceId);
        ws.setProjectRoot(projectRoot);
        save(ws);
        log.info("[Workspace] Set project root: {} in workspace {}", projectRoot, workspaceId);
    }

    private Workspace toWorkspace(WorkspaceEntity entity) {
        Workspace ws = new Workspace();
        ws.setWorkspaceId(entity.getWorkspaceId());
        ws.setName(entity.getName());
        ws.setProjectPath(entity.getProjectPath());
        ws.setProjectRoot(entity.getProjectRoot());
        ws.setActiveTasks(fromJsonTaskList(entity.getActiveTasks()));
        ws.setTodos(fromJsonTodoList(entity.getTodos()));
        ws.setGitState(fromJson(entity.getGitState(), GIT_STATE_TYPE));
        ws.setTerminalState(fromJson(entity.getTerminalState(), TERMINAL_STATE_TYPE));
        ws.setFileTreeSnapshot(fromJsonStringList(entity.getFileTreeSnapshot()));
        ws.setHostContexts(fromJsonMap(entity.getHostContexts()));
        ws.setOpenedFiles(fromJsonOpenedFiles(entity.getOpenedFiles()));
        ws.setArtifacts(fromJsonArtifactList(entity.getArtifacts()));
        ws.setLastActiveFile(entity.getLastActiveFile());
        ws.setLastActiveLine(entity.getLastActiveLine() != null ? entity.getLastActiveLine() : 0);
        ws.setLastOpenedFile(fromJsonOpenedFile(entity.getLastOpenedFile()));
        ws.setLastActiveAt(toInstant(entity.getLastActiveAt()));
        ws.setCreatedAt(toInstant(entity.getCreatedAt()));
        ws.setUpdatedAt(toInstant(entity.getUpdatedAt()));
        return ws;
    }

    private List<Workspace.Task> fromJsonTaskList(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, TASK_LIST_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("[Workspace] Failed to parse task list: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Workspace.Todo> fromJsonTodoList(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, TODO_LIST_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("[Workspace] Failed to parse todo list: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<String> fromJsonStringList(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("[Workspace] Failed to parse string list: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private Map<String, String> fromJsonMap(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, STRING_MAP_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("[Workspace] Failed to parse map: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Workspace.OpenedFile> fromJsonOpenedFiles(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, OPENED_FILES_MAP_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("[Workspace] Failed to parse opened files: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private Workspace.OpenedFile fromJsonOpenedFile(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) return null;
        try {
            return objectMapper.readValue(json, OPENED_FILE_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("[Workspace] Failed to parse opened file: {}", e.getMessage());
            return null;
        }
    }

    private List<Artifact> fromJsonArtifactList(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, ARTIFACT_LIST_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("[Workspace] Failed to parse artifacts: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) return null;
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.warn("[Workspace] Failed to parse JSON: {}", e.getMessage());
            return null;
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("[Workspace] Failed to serialize to JSON: {}", e.getMessage());
            return null;
        }
    }

    private Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.atZone(ZoneId.systemDefault()).toInstant() : null;
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneId.systemDefault()) : null;
    }
}