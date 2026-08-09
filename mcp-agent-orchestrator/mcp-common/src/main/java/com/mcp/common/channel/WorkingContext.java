package com.mcp.common.channel;

import java.time.Instant;

/**
 * WorkingContext — 运行时工作上下文。
 *
 * 与 SessionState 的区别：
 * - SessionState = Session Configuration（voiceMode, language, mode, roleRuntime）
 *   生命周期 = 整个会话
 * - WorkingContext = Runtime Working State（activeArtifact, summary, currentTask）
 *   生命周期 = 一次任务，任务结束自动释放
 *
 * 设计原则：
 * 1. 不把运行时状态塞进 SessionState，避免 SessionState 越来越胖
 * 2. 未来 Agent → SubTask → Planner → Worker 各有自己的 WorkingContext
 * 3. 与 Artifact 的 Summary 缓存解耦：Summary 缓存到 Artifact.metadata
 */
public class WorkingContext {

    /** 当前活跃的 Artifact ID */
    private String activeArtifactId;

    /** 当前活跃的文档路径 */
    private String activeDocumentPath;

    /** 活跃文档的摘要缓存（首次 Recall 生成，后续复用） */
    private String activeDocumentSummary;

    /** 当前活跃上下文来源 */
    private ActiveContextSource activeContextSource = ActiveContextSource.NONE;

    /** 上一次的上下文加载类型 */
    private ContextRequirement lastContextType = ContextRequirement.NONE;

    /** 当前任务描述 */
    private String currentTask;

    /** 当前工作区 ID */
    private String currentWorkspace;

    /** 最后活跃时间 */
    private Instant lastActiveAt = Instant.now();

    public WorkingContext() {}

    public boolean hasActiveDocument() {
        return activeDocumentPath != null && !activeDocumentPath.isBlank();
    }

    public boolean hasActiveArtifact() {
        return activeArtifactId != null && !activeArtifactId.isBlank();
    }

    public boolean needsDocumentContext() {
        return hasActiveDocument()
                || hasActiveArtifact()
                || activeContextSource == ActiveContextSource.GAME
                || activeContextSource == ActiveContextSource.ARTIFACT;
    }

    public void setActiveDocument(String path, String artifactId) {
        this.activeDocumentPath = path;
        this.activeArtifactId = artifactId;
        this.activeContextSource = ActiveContextSource.ARTIFACT;
        this.lastActiveAt = Instant.now();
    }

    public void clearActiveDocument() {
        this.activeDocumentPath = null;
        this.activeArtifactId = null;
        this.activeDocumentSummary = null;
        if (this.activeContextSource == ActiveContextSource.ARTIFACT) {
            this.activeContextSource = ActiveContextSource.NONE;
        }
    }

    public void setGameActive(String task) {
        this.activeContextSource = ActiveContextSource.GAME;
        this.currentTask = task;
        this.lastActiveAt = Instant.now();
    }

    public void touch() {
        this.lastActiveAt = Instant.now();
    }

    public String getActiveArtifactId() { return activeArtifactId; }
    public void setActiveArtifactId(String v) { this.activeArtifactId = v; }

    public String getActiveDocumentPath() { return activeDocumentPath; }
    public void setActiveDocumentPath(String v) { this.activeDocumentPath = v; }

    public String getActiveDocumentSummary() { return activeDocumentSummary; }
    public void setActiveDocumentSummary(String v) { this.activeDocumentSummary = v; }

    public ActiveContextSource getActiveContextSource() { return activeContextSource; }
    public void setActiveContextSource(ActiveContextSource v) { this.activeContextSource = v; }

    public ContextRequirement getLastContextType() { return lastContextType; }
    public void setLastContextType(ContextRequirement v) { this.lastContextType = v; }

    public String getCurrentTask() { return currentTask; }
    public void setCurrentTask(String v) { this.currentTask = v; }

    public String getCurrentWorkspace() { return currentWorkspace; }
    public void setCurrentWorkspace(String v) { this.currentWorkspace = v; }

    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant v) { this.lastActiveAt = v; }
}