-- V15: 创建工作空间表 (Workspaces)
-- Agent 工作空间持久化，跨会话、跨 Host 恢复工作上下文
SET search_path TO mcp_agent;

-- =============================================
-- Workspaces 表
-- =============================================
CREATE TABLE IF NOT EXISTS workspaces (
    workspace_id        VARCHAR(128) PRIMARY KEY,
    name                VARCHAR(200),
    project_path        VARCHAR(1000),
    active_tasks        JSONB,
    todos               JSONB,
    git_state           JSONB,
    terminal_state      JSONB,
    file_tree_snapshot  JSONB,
    host_contexts       JSONB,
    last_active_file    VARCHAR(1000),
    last_active_line    INTEGER,
    memory_index        JSONB,
    last_active_at      TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_workspace_name ON workspaces(name);
CREATE INDEX IF NOT EXISTS idx_workspace_project_path ON workspaces(project_path);
CREATE INDEX IF NOT EXISTS idx_workspace_last_active ON workspaces(last_active_at DESC);

COMMENT ON TABLE workspaces IS '工作空间表 - 持久化 Agent 工作上下文';
COMMENT ON COLUMN workspaces.workspace_id IS '工作空间唯一标识';
COMMENT ON COLUMN workspaces.name IS '工作空间名称';
COMMENT ON COLUMN workspaces.project_path IS '关联的项目路径';
COMMENT ON COLUMN workspaces.active_tasks IS '当前活跃任务列表 (JSON)';
COMMENT ON COLUMN workspaces.todos IS '待办事项列表 (JSON)';
COMMENT ON COLUMN workspaces.git_state IS 'Git 状态快照 (JSON)';
COMMENT ON COLUMN workspaces.terminal_state IS '终端状态快照 (JSON)';
COMMENT ON COLUMN workspaces.file_tree_snapshot IS '文件树快照 (JSON)';
COMMENT ON COLUMN workspaces.host_contexts IS '各 Host 上下文快照 (JSON)';
COMMENT ON COLUMN workspaces.last_active_file IS '最后活跃的文件路径';
COMMENT ON COLUMN workspaces.last_active_line IS '最后活跃的行号';
COMMENT ON COLUMN workspaces.memory_index IS '记忆索引 (JSON)';
COMMENT ON COLUMN workspaces.last_active_at IS '最后活跃时间';