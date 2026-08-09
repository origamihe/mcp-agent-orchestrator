-- =============================================
-- V14: Google AI Studio 默认 LLM 配置
-- 使用 OpenAI Compatible API 接入 Gemini
-- =============================================

-- 插入 Google AI Studio Gemini 2.5 Flash 默认配置
INSERT INTO mcp_agent.llm_config (config_id, provider, model_name, temperature, max_tokens, parameters, enabled, created_at, updated_at)
VALUES (
    'default-google-gemini',
    'GOOGLE_GENAI',
    'gemini-2.5-flash',
    0.7,
    4096,
    '{"baseUrl": "https://generativelanguage.googleapis.com/v1beta/openai/"}',
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT (config_id) DO NOTHING;

-- 插入 Gemini 2.5 Pro 备选配置
INSERT INTO mcp_agent.llm_config (config_id, provider, model_name, temperature, max_tokens, parameters, enabled, created_at, updated_at)
VALUES (
    'google-gemini-pro',
    'GOOGLE_GENAI',
    'gemini-2.5-pro',
    0.5,
    8192,
    '{"baseUrl": "https://generativelanguage.googleapis.com/v1beta/openai/"}',
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT (config_id) DO NOTHING;