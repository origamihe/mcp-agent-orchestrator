ALTER TABLE mcp_agent.chat_sessions
    ADD COLUMN IF NOT EXISTS agent_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'active';

CREATE INDEX IF NOT EXISTS idx_chat_sessions_agent_id
    ON mcp_agent.chat_sessions(agent_id);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_status
    ON mcp_agent.chat_sessions(status);