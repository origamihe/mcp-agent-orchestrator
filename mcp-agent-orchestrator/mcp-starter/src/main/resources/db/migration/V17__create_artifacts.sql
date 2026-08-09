-- V17: 创建 artifacts 表
-- Artifact 与 Memory 完全解耦：Memory 存储长期知识，Artifact 管理临时可编辑对象
SET search_path TO mcp_agent;

CREATE TABLE IF NOT EXISTS artifacts (
    id              VARCHAR(64) PRIMARY KEY,
    session_id      VARCHAR(128) NOT NULL,
    artifact_type   VARCHAR(32) NOT NULL,
    file_path       VARCHAR(1024),
    content         TEXT,
    encoding        VARCHAR(32) DEFAULT 'UTF-8',
    size_bytes      BIGINT DEFAULT 0,
    version         INT DEFAULT 1,
    is_dirty        BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    modified_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_artifacts_session ON artifacts(session_id);
CREATE INDEX IF NOT EXISTS idx_artifacts_type ON artifacts(session_id, artifact_type);
CREATE INDEX IF NOT EXISTS idx_artifacts_path ON artifacts(session_id, file_path);
CREATE INDEX IF NOT EXISTS idx_artifacts_deleted ON artifacts(deleted_at) WHERE deleted_at IS NULL;

COMMENT ON TABLE artifacts IS 'Artifact 表 — 临时可编辑对象（文件、Prompt、代码、Markdown 等），与长期 Memory 完全解耦';
COMMENT ON COLUMN artifacts.artifact_type IS 'FILE, CODE, PROMPT, MARKDOWN, SQL, DIFF, LOG, CONFIG, TEXT, IMAGE, PDF, EXCEL, WEB, REPORT';
COMMENT ON COLUMN artifacts.file_path IS '文件路径（FILE 类型时有效）';
COMMENT ON COLUMN artifacts.is_dirty IS '是否已修改但未持久化到磁盘';