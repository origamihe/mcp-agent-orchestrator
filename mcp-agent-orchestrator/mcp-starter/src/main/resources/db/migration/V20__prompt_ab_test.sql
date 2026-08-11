-- =============================================
-- V20: Prompt 版本管理 + A/B 测试支持
-- =============================================

-- 1. prompt_templates 表增加 A/B 变体支持
ALTER TABLE prompt_templates
    ADD COLUMN IF NOT EXISTS variant VARCHAR(50) DEFAULT 'default',
    ADD COLUMN IF NOT EXISTS weight DOUBLE PRECISION DEFAULT 1.0,
    ADD COLUMN IF NOT EXISTS enabled BOOLEAN DEFAULT true,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 移除 name 主键约束，改为 (name, variant, version) 联合主键
-- 先删除外键依赖（如果有的话），再重建主键
ALTER TABLE prompt_templates DROP CONSTRAINT IF EXISTS prompt_templates_pkey;
ALTER TABLE prompt_templates
    ADD CONSTRAINT prompt_templates_pkey PRIMARY KEY (name, variant, version);

-- 2. prompt_ab_results 表 — 记录 A/B 测试效果
CREATE TABLE IF NOT EXISTS prompt_ab_results (
    id              BIGSERIAL PRIMARY KEY,
    prompt_name     VARCHAR(100) NOT NULL,
    variant         VARCHAR(50) NOT NULL,
    call_count      BIGINT DEFAULT 0,
    total_duration_ms BIGINT DEFAULT 0,
    total_tokens    BIGINT DEFAULT 0,
    success_count   BIGINT DEFAULT 0,
    failure_count   BIGINT DEFAULT 0,
    avg_rating      DOUBLE PRECISION DEFAULT 0.0,
    rating_count    BIGINT DEFAULT 0,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(prompt_name, variant)
);

CREATE INDEX IF NOT EXISTS idx_pab_prompt ON prompt_ab_results(prompt_name);
CREATE INDEX IF NOT EXISTS idx_pab_variant ON prompt_ab_results(variant);

COMMENT ON TABLE prompt_ab_results IS 'Prompt A/B 测试效果统计表';
COMMENT ON COLUMN prompt_ab_results.call_count IS '调用次数';
COMMENT ON COLUMN prompt_ab_results.avg_rating IS '平均用户评分 (0-5)';