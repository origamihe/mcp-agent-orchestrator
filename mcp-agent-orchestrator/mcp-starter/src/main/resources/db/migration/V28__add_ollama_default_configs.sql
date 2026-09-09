-- =============================================
-- V28: Ollama 本地默认 LLM 配置
-- 补充运行时默认可用的 Ollama 模型配置
-- qwen3:8b 为当前默认推荐模型
-- =============================================

-- 插入 Ollama qwen3:8b 默认配置
INSERT INTO mcp_agent.llm_config (config_id, provider, model_name, temperature, max_tokens, parameters, enabled, created_at, updated_at)
VALUES (
    'default-ollama-qwen3',
    'LOCAL_OLLAMA',
    'qwen3:8b',
    0.3,
    4096,
    '{}',
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT (config_id) DO NOTHING;

-- 插入 Ollama qwen2:7b 备选配置
INSERT INTO mcp_agent.llm_config (config_id, provider, model_name, temperature, max_tokens, parameters, enabled, created_at, updated_at)
VALUES (
    'ollama-qwen2',
    'LOCAL_OLLAMA',
    'qwen2:7b',
    0.3,
    4096,
    '{}',
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT (config_id) DO NOTHING;