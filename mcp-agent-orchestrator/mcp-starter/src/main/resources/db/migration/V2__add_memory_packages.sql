-- =============================================
-- MCP Agent Orchestrator - V2: 长记忆系统
-- =============================================
SET search_path TO mcp_agent;

-- =============================================
-- Memory Packages 表（压缩记忆层）
-- =============================================
CREATE TABLE memory_packages (
                                 id              BIGSERIAL PRIMARY KEY,
                                 session_id      VARCHAR(64) NOT NULL,
                                 category        VARCHAR(30) NOT NULL,
                                 content         TEXT NOT NULL,
                                 metadata        JSONB,
                                 version         INTEGER NOT NULL DEFAULT 1,
                                 access_count    INTEGER NOT NULL DEFAULT 0,
                                 weight          DOUBLE PRECISION NOT NULL DEFAULT 1.0,
                                 created_at      TIMESTAMP NOT NULL,
                                 last_accessed_at TIMESTAMP NOT NULL,

                                 CONSTRAINT fk_memory_session
                                     FOREIGN KEY (session_id)
                                         REFERENCES chat_sessions(session_id)
                                         ON DELETE CASCADE
);

-- 索引：按会话+分类+版本查询
CREATE INDEX idx_memory_session ON memory_packages(session_id);
CREATE INDEX idx_memory_category ON memory_packages(session_id, category);
CREATE INDEX idx_memory_weight ON memory_packages(session_id, weight DESC);
CREATE INDEX idx_memory_last_access ON memory_packages(last_accessed_at);

COMMENT ON TABLE memory_packages IS '压缩记忆包 - 三层记忆架构的第二层';
COMMENT ON COLUMN memory_packages.category IS 'USER_PREFERENCES|PROJECT_CONTEXT|CONFIRMED_FACTS|OPEN_TASKS|DECISION_HISTORY|IMPORTANT_CONSTRAINTS|SUMMARY|LONG_TERM';
COMMENT ON COLUMN memory_packages.metadata IS 'JSON格式: {basis, scope, expirationCondition}';
COMMENT ON COLUMN memory_packages.weight IS '权重，用于衰减机制和检索排序';