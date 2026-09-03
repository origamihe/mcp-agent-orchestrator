-- Add agentId and sessionId columns to trace_events for log filtering
ALTER TABLE mcp_agent.trace_events
    ADD COLUMN IF NOT EXISTS agent_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS session_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_trace_events_agent_id ON mcp_agent.trace_events(agent_id);
CREATE INDEX IF NOT EXISTS idx_trace_events_session_id ON mcp_agent.trace_events(session_id);