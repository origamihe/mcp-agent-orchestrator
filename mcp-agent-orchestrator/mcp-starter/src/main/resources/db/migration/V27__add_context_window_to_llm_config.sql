-- =============================================
-- MCP Agent Orchestrator - V27
-- 添加 context_window 列到 llm_config 表
-- 修复 Entity (LlmConfigEntity) 与 DB Schema 之间的漂移
-- =============================================

ALTER TABLE mcp_agent.llm_config
ADD COLUMN IF NOT EXISTS context_window INTEGER DEFAULT 128000;

COMMENT ON COLUMN mcp_agent.llm_config.context_window IS 'LLM 上下文窗口大小（token 数）';