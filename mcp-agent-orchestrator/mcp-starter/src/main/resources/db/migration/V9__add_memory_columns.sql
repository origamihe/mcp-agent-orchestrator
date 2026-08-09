-- V9: 补充 memory_packages 缺失列（confidence, upgrade_count, ttl, source_quote）
SET search_path TO mcp_agent;

ALTER TABLE memory_packages
    ADD COLUMN IF NOT EXISTS confidence INTEGER NOT NULL DEFAULT 50,
    ADD COLUMN IF NOT EXISTS upgrade_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ttl TIMESTAMP,
    ADD COLUMN IF NOT EXISTS source_quote TEXT;

COMMENT ON COLUMN memory_packages.confidence IS '置信度评分 0-100';
COMMENT ON COLUMN memory_packages.upgrade_count IS '升级计数，达到3次触发记忆类型升级';
COMMENT ON COLUMN memory_packages.ttl IS '过期时间';
COMMENT ON COLUMN memory_packages.source_quote IS '原始对话引用';