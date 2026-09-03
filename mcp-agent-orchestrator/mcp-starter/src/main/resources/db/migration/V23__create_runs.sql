CREATE TABLE IF NOT EXISTS mcp_agent.runs (
    id              VARCHAR(64)     NOT NULL PRIMARY KEY,
    agent_id        VARCHAR(100)    NOT NULL,
    agent_name      VARCHAR(200),
    session_id      VARCHAR(64)     NOT NULL,
    intent          VARCHAR(200),
    status          VARCHAR(20)     NOT NULL DEFAULT 'pending',
    duration_ms     BIGINT,
    tool_call_count INTEGER         DEFAULT 0,
    prompt_tokens   INTEGER         DEFAULT 0,
    completion_tokens INTEGER       DEFAULT 0,
    total_tokens    INTEGER         DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP,
    CONSTRAINT fk_runs_session FOREIGN KEY (session_id)
        REFERENCES mcp_agent.chat_sessions(session_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_runs_agent_id    ON mcp_agent.runs(agent_id);
CREATE INDEX IF NOT EXISTS idx_runs_session_id   ON mcp_agent.runs(session_id);
CREATE INDEX IF NOT EXISTS idx_runs_status       ON mcp_agent.runs(status);
CREATE INDEX IF NOT EXISTS idx_runs_created_at   ON mcp_agent.runs(created_at);