-- V8: Skill Graph - Skill 依赖关系建模
-- 支持 Skill 间的共现关系、前置依赖、替代关系
SET search_path TO mcp_agent;

CREATE TABLE IF NOT EXISTS skill_dependencies (
    id                  BIGSERIAL PRIMARY KEY,
    source_skill_id     BIGINT NOT NULL,
    target_skill_id     BIGINT NOT NULL,
    dependency_type     VARCHAR(20) NOT NULL DEFAULT 'FOLLOWS',
    co_occurrence_count INTEGER NOT NULL DEFAULT 1,
    confidence          DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_source_skill FOREIGN KEY (source_skill_id) REFERENCES skill_library(id) ON DELETE CASCADE,
    CONSTRAINT fk_target_skill FOREIGN KEY (target_skill_id) REFERENCES skill_library(id) ON DELETE CASCADE
);

CREATE INDEX idx_sd_source ON skill_dependencies(source_skill_id, co_occurrence_count DESC);
CREATE INDEX idx_sd_target ON skill_dependencies(target_skill_id, co_occurrence_count DESC);
CREATE UNIQUE INDEX idx_sd_pair ON skill_dependencies(source_skill_id, target_skill_id, dependency_type);
CREATE INDEX idx_sd_confidence ON skill_dependencies(source_skill_id, confidence DESC);