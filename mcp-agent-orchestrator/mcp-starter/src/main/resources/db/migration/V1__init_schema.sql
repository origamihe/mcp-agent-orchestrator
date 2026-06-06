-- =============================================
-- MCP Agent Orchestrator - PostgreSQL 初始化脚本
-- Version: V1
-- =============================================

-- 创建 Schema
CREATE SCHEMA IF NOT EXISTS mcp_agent;
SET search_path TO mcp_agent;

-- =============================================
-- 1. Chat Sessions 表
-- =============================================
CREATE TABLE chat_sessions (
    session_id      VARCHAR(64) PRIMARY KEY,
    user_id         VARCHAR(100),
    created_at      TIMESTAMP NOT NULL,
    last_active_at  TIMESTAMP NOT NULL,

    created_at_tz   TIMESTAMPTZ GENERATED ALWAYS AS (created_at AT TIME ZONE 'Asia/Shanghai') STORED,
    last_active_at_tz TIMESTAMPTZ GENERATED ALWAYS AS (last_active_at AT TIME ZONE 'Asia/Shanghai') STORED
);

-- 索引
CREATE INDEX idx_session_user ON chat_sessions(user_id);
CREATE INDEX idx_last_active ON chat_sessions(last_active_at);

-- =============================================
-- 2. Chat Messages 表
-- =============================================
CREATE TABLE chat_messages (
    id            BIGSERIAL PRIMARY KEY,
    session_id    VARCHAR(64) NOT NULL,
    role          VARCHAR(20) NOT NULL,
    content       TEXT NOT NULL,
    tool_calls    JSONB,                    -- 推荐使用 JSONB
    created_at    TIMESTAMP NOT NULL,

    created_at_tz TIMESTAMPTZ GENERATED ALWAYS AS (created_at AT TIME ZONE 'Asia/Shanghai') STORED,

    CONSTRAINT fk_message_session 
        FOREIGN KEY (session_id) 
        REFERENCES chat_sessions(session_id) 
        ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_message_session ON chat_messages(session_id);
CREATE INDEX idx_message_created ON chat_messages(created_at);
CREATE INDEX idx_message_role ON chat_messages(role);

-- =============================================
-- 3. LLM Config 表
-- =============================================
CREATE TABLE llm_config (
    config_id     VARCHAR(100) PRIMARY KEY,
    provider      VARCHAR(50) NOT NULL,
    model_name    VARCHAR(100) NOT NULL,
    temperature   DECIMAL(4,2) DEFAULT 0.7,
    max_tokens    INTEGER DEFAULT 2048,
    parameters    JSONB,                    -- 推荐使用 JSONB
    enabled       BOOLEAN DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL,

    created_at_tz TIMESTAMPTZ GENERATED ALWAYS AS (created_at AT TIME ZONE 'Asia/Shanghai') STORED,
    updated_at_tz TIMESTAMPTZ GENERATED ALWAYS AS (updated_at AT TIME ZONE 'Asia/Shanghai') STORED
);

CREATE INDEX idx_llm_provider ON llm_config(provider);
CREATE INDEX idx_llm_model ON llm_config(model_name);

-- =============================================
-- 4. Prompt Templates 表
-- =============================================
CREATE TABLE prompt_templates (
    name           VARCHAR(100) PRIMARY KEY,
    type           VARCHAR(50) NOT NULL,
    template_text  TEXT NOT NULL,
    description    VARCHAR(500),
    version        INTEGER DEFAULT 1,
    updated_at     TIMESTAMP NOT NULL,

    updated_at_tz  TIMESTAMPTZ GENERATED ALWAYS AS (updated_at AT TIME ZONE 'Asia/Shanghai') STORED
);

CREATE INDEX idx_prompt_type ON prompt_templates(type);
CREATE INDEX idx_prompt_name ON prompt_templates(name);

-- =============================================
-- 注释（可选，提高可读性）
-- =============================================
COMMENT ON TABLE chat_sessions IS '聊天会话表';
COMMENT ON TABLE chat_messages IS '聊天消息表';
COMMENT ON TABLE llm_config IS 'LLM 配置表';
COMMENT ON TABLE prompt_templates IS '提示词模板表';