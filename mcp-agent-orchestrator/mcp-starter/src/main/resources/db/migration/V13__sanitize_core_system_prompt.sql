-- =============================================
-- V13: 清理 core_system prompt 中的 [Internal_Memory_Storage] 指令
-- =============================================
-- 背景：后台 MemoryLifecycleOrchestrator 已使用独立 LLM 调用（MemoryExtractor）
-- 处理记忆抽取，聊天模型不应再承担 [Internal_Memory_Storage] 输出职责。
-- 此迁移移除 core_system prompt 中的相关指令，防止 Tool Leakage。

UPDATE prompt_templates
SET template_text = regexp_replace(
    template_text,
    '\[Internal_Memory_Storage\][^\n]*(\n\{[^}]*\})*',
    '',
    'g'
)
WHERE name = 'core_system'
  AND template_text ~* '\[Internal_Memory_Storage\]';

-- 同时清理其他可能包含此指令的 prompt 模板
UPDATE prompt_templates
SET template_text = regexp_replace(
    template_text,
    '\[Internal_Memory_Storage\][^\n]*(\n\{[^}]*\})*',
    '',
    'g'
)
WHERE template_text ~* '\[Internal_Memory_Storage\]';