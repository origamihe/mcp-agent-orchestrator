CREATE TABLE IF NOT EXISTS mcp_agent.trace_events (
    id              BIGSERIAL       NOT NULL PRIMARY KEY,
    run_id          VARCHAR(64)     NOT NULL,
    parent_id       BIGINT,
    operation       VARCHAR(200)    NOT NULL,
    event_type      VARCHAR(50)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'success',
    start_time      TIMESTAMP       NOT NULL,
    end_time        TIMESTAMP,
    duration_ms     BIGINT,
    sequence        INTEGER         NOT NULL DEFAULT 0,
    payload         JSONB,
    metadata        JSONB,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_trace_events_run FOREIGN KEY (run_id)
        REFERENCES mcp_agent.runs(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_trace_events_run_id     ON mcp_agent.trace_events(run_id);
CREATE INDEX IF NOT EXISTS idx_trace_events_parent_id   ON mcp_agent.trace_events(parent_id);
CREATE INDEX IF NOT EXISTS idx_trace_events_sequence    ON mcp_agent.trace_events(run_id, sequence);