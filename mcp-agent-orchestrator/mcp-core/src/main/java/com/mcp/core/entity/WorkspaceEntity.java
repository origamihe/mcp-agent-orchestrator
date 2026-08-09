package com.mcp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 工作空间持久化实体。
 * 每个 workspace 维护一个独立的 Agent 工作状态，跨会话、跨 Host 持久化。
 * Agent 启动时通过 workspaceId 恢复完整的工作上下文。
 */
@Entity
@Table(name = "workspaces", schema = "mcp_agent")
public class WorkspaceEntity {

    @Id
    @Column(name = "workspace_id", length = 128, nullable = false, unique = true)
    private String workspaceId;

    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "project_path", length = 1000)
    private String projectPath;

    @Column(name = "project_root", length = 1000)
    private String projectRoot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "active_tasks", columnDefinition = "jsonb")
    private String activeTasks;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "todos", columnDefinition = "jsonb")
    private String todos;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "git_state", columnDefinition = "jsonb")
    private String gitState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "terminal_state", columnDefinition = "jsonb")
    private String terminalState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "file_tree_snapshot", columnDefinition = "jsonb")
    private String fileTreeSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "host_contexts", columnDefinition = "jsonb")
    private String hostContexts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "opened_files", columnDefinition = "jsonb")
    private String openedFiles;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "artifacts", columnDefinition = "jsonb")
    private String artifacts;

    @Column(name = "last_active_file", length = 1000)
    private String lastActiveFile;

    @Column(name = "last_active_line")
    private Integer lastActiveLine;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_opened_file", columnDefinition = "jsonb")
    private String lastOpenedFile;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "memory_index", columnDefinition = "jsonb")
    private String memoryIndex;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public WorkspaceEntity() {}

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }

    public String getProjectRoot() { return projectRoot; }
    public void setProjectRoot(String projectRoot) { this.projectRoot = projectRoot; }

    public String getActiveTasks() { return activeTasks; }
    public void setActiveTasks(String activeTasks) { this.activeTasks = activeTasks; }

    public String getTodos() { return todos; }
    public void setTodos(String todos) { this.todos = todos; }

    public String getGitState() { return gitState; }
    public void setGitState(String gitState) { this.gitState = gitState; }

    public String getTerminalState() { return terminalState; }
    public void setTerminalState(String terminalState) { this.terminalState = terminalState; }

    public String getFileTreeSnapshot() { return fileTreeSnapshot; }
    public void setFileTreeSnapshot(String fileTreeSnapshot) { this.fileTreeSnapshot = fileTreeSnapshot; }

    public String getHostContexts() { return hostContexts; }
    public void setHostContexts(String hostContexts) { this.hostContexts = hostContexts; }

    public String getOpenedFiles() { return openedFiles; }
    public void setOpenedFiles(String openedFiles) { this.openedFiles = openedFiles; }

    public String getArtifacts() { return artifacts; }
    public void setArtifacts(String artifacts) { this.artifacts = artifacts; }

    public String getLastActiveFile() { return lastActiveFile; }
    public void setLastActiveFile(String lastActiveFile) { this.lastActiveFile = lastActiveFile; }

    public Integer getLastActiveLine() { return lastActiveLine; }
    public void setLastActiveLine(Integer lastActiveLine) { this.lastActiveLine = lastActiveLine; }

    public String getLastOpenedFile() { return lastOpenedFile; }
    public void setLastOpenedFile(String lastOpenedFile) { this.lastOpenedFile = lastOpenedFile; }

    public String getMemoryIndex() { return memoryIndex; }
    public void setMemoryIndex(String memoryIndex) { this.memoryIndex = memoryIndex; }

    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}