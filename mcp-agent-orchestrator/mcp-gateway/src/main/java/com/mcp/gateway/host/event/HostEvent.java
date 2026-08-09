package com.mcp.gateway.host.event;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Host 事件模型 — 插件收集的 IDE 事件。
 * 不是聊天，而是环境感知。Gateway 根据事件类型分发给 Memory、Planner、Skill。
 */
@Data
public class HostEvent {

    private String hostType;        // "ide", "desktop", "terminal"
    private String ideType;         // "Rider", "IDEA", "VSCode"
    private String workspaceId;     // 项目唯一标识
    private String sessionId;       // WebSocket 会话

    private EventType type;         // 事件类型
    private Map<String, Object> payload; // 事件负载（轻量）
    private Instant timestamp = Instant.now();

    public HostEvent() {
    }

    public enum EventType {
        // === 生命周期 ===
        PROJECT_OPENED,
        PROJECT_CLOSED,

        // === 编辑器 ===
        FILE_OPENED,
        FILE_CLOSED,
        FILE_SAVED,
        CURSOR_MOVED,
        SELECTION_CHANGED,

        // === 构建/运行 ===
        BUILD_STARTED,
        BUILD_FINISHED,
        BUILD_FAILED,
        RUN_STARTED,
        RUN_FINISHED,
        TEST_FINISHED,

        // === VCS ===
        GIT_COMMIT,
        GIT_BRANCH_CHANGED,
        VCS_CHANGED,

        // === 终端 ===
        TERMINAL_OUTPUT,

        // === 用户交互 ===
        USER_CHAT,
        USER_ACTION
    }
}