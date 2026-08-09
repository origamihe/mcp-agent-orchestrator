-- V12: 为 memory_packages 添加 fact_key 字段，支持规范化去重
-- 解决 "Terraria" / "《Terraria》" / "terraria" 等无法合并的问题

ALTER TABLE mcp_agent.memory_packages
    ADD COLUMN IF NOT EXISTS fact_key VARCHAR(200);

COMMENT ON COLUMN mcp_agent.memory_packages.fact_key IS
    '规范化事实键，用于去重合并。例如 "Terraria" 和 "《Terraria》" 都规范化为 "terraria"';

CREATE INDEX IF NOT EXISTS idx_memory_fact_key
    ON mcp_agent.memory_packages(user_id, fact_key)
    WHERE fact_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_memory_fact_key_group
    ON mcp_agent.memory_packages(group_id, fact_key)
    WHERE fact_key IS NOT NULL AND group_id IS NOT NULL;