-- V19: 同步 ArtifactEntity 与 artifacts 表结构
-- 修复 Entity 与数据库 Schema 的版本漂移（schema drift）
-- 问题：ArtifactEntity 新增了 title、mime_type、metadata、created_by 四个字段，
--       但 V17 建表脚本未包含这些列。
-- 使用 ADD COLUMN IF NOT EXISTS 确保幂等安全。
SET search_path TO mcp_agent;

-- 1. title: Artifact 标题（P0 增强）
ALTER TABLE artifacts
    ADD COLUMN IF NOT EXISTS title VARCHAR(512);

-- 2. mime_type: MIME 类型（P0 增强）
ALTER TABLE artifacts
    ADD COLUMN IF NOT EXISTS mime_type VARCHAR(128);

-- 3. metadata: 扩展元数据（P0 增强）
ALTER TABLE artifacts
    ADD COLUMN IF NOT EXISTS metadata TEXT;

-- 4. created_by: 创建者标识（P0 增强）← 本次报错字段
ALTER TABLE artifacts
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(128);

-- 补充注释
COMMENT ON COLUMN artifacts.title IS 'Artifact 标题';
COMMENT ON COLUMN artifacts.mime_type IS 'MIME 类型（如 text/markdown, application/json）';
COMMENT ON COLUMN artifacts.metadata IS '扩展元数据（JSON 格式）';
COMMENT ON COLUMN artifacts.created_by IS '创建者标识';