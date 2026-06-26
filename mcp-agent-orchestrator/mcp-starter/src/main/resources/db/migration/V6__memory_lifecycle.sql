-- 文件路径: mcp-starter/src/main/resources/db/migration/V6__memory_lifecycle.sql
-- V6: 记忆生命周期系统 - Memory Judge / Merge / GC
SET search_path TO mcp_agent;

-- 1. 添加 memory_type 列
ALTER TABLE memory_packages
    ADD COLUMN IF NOT EXISTS memory_type VARCHAR(20) DEFAULT 'FACT';

-- 2. 添加 importance 评分 (0-100)
ALTER TABLE memory_packages
    ADD COLUMN IF NOT EXISTS importance INTEGER DEFAULT 50;

-- 3. 添加 decay_rate (每日衰减系数，默认 1.0 表示不衰减)
ALTER TABLE memory_packages
    ADD COLUMN IF NOT EXISTS decay_rate DOUBLE PRECISION DEFAULT 1.0;

-- 4. 添加 merge 追踪
ALTER TABLE memory_packages
    ADD COLUMN IF NOT EXISTS merged_from_ids JSONB,
    ADD COLUMN IF NOT EXISTS superseded_by_id BIGINT;

-- 5. 添加软删除标记
ALTER TABLE memory_packages
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;

-- 6. 为 memory_type 创建索引
CREATE INDEX IF NOT EXISTS idx_memory_type ON memory_packages(memory_type);
CREATE INDEX IF NOT EXISTS idx_memory_importance ON memory_packages(importance DESC);
CREATE INDEX IF NOT EXISTS idx_memory_last_accessed ON memory_packages(last_accessed_at);
CREATE INDEX IF NOT EXISTS idx_memory_active_type ON memory_packages(is_active, memory_type);

-- 7. 为现有数据设置默认 memory_type（基于 category 映射）
UPDATE memory_packages SET memory_type = 'PREFERENCE' WHERE category = 'USER_PREFERENCES' AND memory_type = 'FACT';
UPDATE memory_packages SET memory_type = 'PROJECT' WHERE category = 'PROJECT_CONTEXT' AND memory_type = 'FACT';
UPDATE memory_packages SET memory_type = 'FACT' WHERE category = 'CONFIRMED_FACTS' AND memory_type = 'FACT';
UPDATE memory_packages SET memory_type = 'GOAL' WHERE category = 'OPEN_TASKS' AND memory_type = 'FACT';
UPDATE memory_packages SET memory_type = 'FACT' WHERE category = 'DECISION_HISTORY' AND memory_type = 'FACT';
UPDATE memory_packages SET memory_type = 'FACT' WHERE category = 'IMPORTANT_CONSTRAINTS' AND memory_type = 'FACT';
UPDATE memory_packages SET memory_type = 'EVENT' WHERE category = 'SUMMARY' AND memory_type = 'FACT';
UPDATE memory_packages SET memory_type = 'FACT' WHERE category = 'LONG_TERM' AND memory_type = 'FACT';