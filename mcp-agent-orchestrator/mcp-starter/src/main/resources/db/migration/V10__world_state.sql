-- V10: 世界状态持久化表（跑团/NPC/长期任务的世界上下文）
SET search_path TO mcp_agent;

CREATE TABLE IF NOT EXISTS world_states (
    id                BIGSERIAL PRIMARY KEY,
    session_id        VARCHAR(64)  NOT NULL UNIQUE,
    game_time         VARCHAR(200),
    current_location  VARCHAR(500),
    weather           VARCHAR(200),
    atmosphere        VARCHAR(500),
    npcs              JSONB        DEFAULT '[]',
    active_events     JSONB        DEFAULT '[]',
    world_rules       JSONB        DEFAULT '{}',
    recent_happenings JSONB        DEFAULT '[]',
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE world_states IS '世界状态表 — 为跑团、NPC、长期任务维护独立的世界上下文';
COMMENT ON COLUMN world_states.session_id IS '会话 ID，一对一绑定';
COMMENT ON COLUMN world_states.game_time IS '当前游戏内时间';
COMMENT ON COLUMN world_states.current_location IS '当前游戏内位置';
COMMENT ON COLUMN world_states.weather IS '天气';
COMMENT ON COLUMN world_states.atmosphere IS '氛围描述';
COMMENT ON COLUMN world_states.npcs IS '在场 NPC 列表（JSON 数组）';
COMMENT ON COLUMN world_states.active_events IS '进行中的事件（JSON 数组）';
COMMENT ON COLUMN world_states.world_rules IS '世界规则（JSON 对象）';
COMMENT ON COLUMN world_states.recent_happenings IS '最近发生的事件（JSON 数组）';

CREATE INDEX IF NOT EXISTS idx_world_states_session ON world_states(session_id);