-- V7: Agent 学习系统 - Skill Library / Failure Library / Reflection Log
-- Planner → Observation → Evaluator → Reflection → Skill/Failure
SET search_path TO mcp_agent;

-- =============================================
-- 1. 技能库 (Skill Library)
-- 存储可复用的执行模式，支持版本进化
-- =============================================
CREATE TABLE IF NOT EXISTS skill_library (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    triggers            JSONB,              -- 触发词: ["新闻", "today", "最新"]
    steps               JSONB NOT NULL,     -- 执行步骤: [{"tool":"webSearch","params":{"engine":"Google"}}, ...]
    fallback_steps      JSONB,              -- 降级步骤
    version             INTEGER NOT NULL DEFAULT 1,
    success_rate        DOUBLE PRECISION NOT NULL DEFAULT 0.0,  -- 成功率 0.0-100.0
    total_executions    INTEGER NOT NULL DEFAULT 0,
    success_count       INTEGER NOT NULL DEFAULT 0,
    failure_count       INTEGER NOT NULL DEFAULT 0,
    evolved_from_id     BIGINT,             -- 从哪个 Skill 进化而来
    dependencies        JSONB,              -- 依赖的 Skill ID 列表
    metadata            JSONB,              -- 额外元数据
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_skill_name ON skill_library(name);
CREATE INDEX IF NOT EXISTS idx_skill_active ON skill_library(is_active) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_skill_success_rate ON skill_library(success_rate DESC);
CREATE INDEX IF NOT EXISTS idx_skill_evolved_from ON skill_library(evolved_from_id) WHERE evolved_from_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_skill_triggers ON skill_library USING GIN (triggers);

COMMENT ON TABLE skill_library IS '技能库 - 存储可复用的工具执行模式';
COMMENT ON COLUMN skill_library.name IS 'Skill 名称，如 "Search Latest News"';
COMMENT ON COLUMN skill_library.triggers IS '触发词 JSON 数组，用于意图匹配';
COMMENT ON COLUMN skill_library.steps IS '执行步骤 JSON 数组，包含 tool/params/priority';
COMMENT ON COLUMN skill_library.fallback_steps IS '降级步骤 JSON 数组，主步骤失败时使用';
COMMENT ON COLUMN skill_library.success_rate IS '成功率 0.0-100.0，每次执行后更新';
COMMENT ON COLUMN skill_library.evolved_from_id IS '从哪个旧版本 Skill 进化而来';
COMMENT ON COLUMN skill_library.dependencies IS '依赖的其他 Skill ID 列表';

-- =============================================
-- 2. 失败库 (Failure Library)
-- 存储"不要这样做"的经验，比 Memory 更重要
-- =============================================
CREATE TABLE IF NOT EXISTS failure_library (
    id                  BIGSERIAL PRIMARY KEY,
    task_pattern        VARCHAR(200) NOT NULL,   -- 任务模式: "Read PDF"
    error_signature     TEXT NOT NULL,            -- 错误签名: "MalformedInputException"
    root_cause          TEXT NOT NULL,            -- 根因: "Used Files.readString() on binary PDF"
    correct_approach    TEXT NOT NULL,            -- 正确做法: "Use PDFBox library instead"
    context_snapshot    JSONB,                    -- 失败时的上下文快照
    occurrence_count    INTEGER NOT NULL DEFAULT 1,
    first_occurred_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    last_occurred_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    is_resolved         BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_by_skill_id BIGINT,                 -- 由哪个 Skill 解决
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_failure_task_pattern ON failure_library(task_pattern);
CREATE INDEX IF NOT EXISTS idx_failure_resolved ON failure_library(is_resolved) WHERE is_resolved = FALSE;
CREATE INDEX IF NOT EXISTS idx_failure_occurrence ON failure_library(occurrence_count DESC);
CREATE INDEX IF NOT EXISTS idx_failure_resolved_by_skill ON failure_library(resolved_by_skill_id) WHERE resolved_by_skill_id IS NOT NULL;

COMMENT ON TABLE failure_library IS '失败库 - 存储"避免再次犯错"的经验';
COMMENT ON COLUMN failure_library.task_pattern IS '任务模式，用于匹配未来类似任务';
COMMENT ON COLUMN failure_library.error_signature IS '错误签名，用于快速匹配';
COMMENT ON COLUMN failure_library.root_cause IS '根因分析，不是表面错误';
COMMENT ON COLUMN failure_library.correct_approach IS '正确做法，避免重复犯错';
COMMENT ON COLUMN failure_library.resolved_by_skill_id IS '关联的 Skill ID，表示该 Skill 解决了此 Failure';

-- =============================================
-- 3. 反思日志 (Reflection Log)
-- 记录每次 Reflection 的完整过程
-- =============================================
CREATE TABLE IF NOT EXISTS reflection_logs (
    id                  BIGSERIAL PRIMARY KEY,
    session_id          VARCHAR(64) NOT NULL,
    user_id             VARCHAR(64),
    user_request        TEXT NOT NULL,
    agent_execution     TEXT NOT NULL,
    tools_used          JSONB,
    task_success        BOOLEAN NOT NULL DEFAULT TRUE,
    failure_reason      TEXT,
    reflection          TEXT,
    worth_learning      BOOLEAN NOT NULL DEFAULT FALSE,
    generated_skill_id  BIGINT,
    generated_failure_id BIGINT,
    outcome             VARCHAR(20) NOT NULL DEFAULT 'DISCARDED',
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reflection_session ON reflection_logs(session_id);
CREATE INDEX IF NOT EXISTS idx_reflection_user ON reflection_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_reflection_worth ON reflection_logs(worth_learning) WHERE worth_learning = TRUE;
CREATE INDEX IF NOT EXISTS idx_reflection_outcome ON reflection_logs(outcome);
CREATE INDEX IF NOT EXISTS idx_reflection_skill ON reflection_logs(generated_skill_id) WHERE generated_skill_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_reflection_failure ON reflection_logs(generated_failure_id) WHERE generated_failure_id IS NOT NULL;

COMMENT ON TABLE reflection_logs IS '反思日志 - 记录每次 Reflection 的完整过程';
COMMENT ON COLUMN reflection_logs.outcome IS 'DISCARDED / SKILL_GENERATED / SKILL_UPDATED / FAILURE_RECORDED / FAILURE_RESOLVED';
COMMENT ON COLUMN reflection_logs.worth_learning IS '是否值得学习 - 由 TaskEvaluator 判定';
COMMENT ON COLUMN reflection_logs.reflection IS 'Reflection Agent 的完整反思输出';