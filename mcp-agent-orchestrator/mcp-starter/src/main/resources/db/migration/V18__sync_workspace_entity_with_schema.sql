-- V18: 同步 WorkspaceEntity 与 workspaces 表结构
-- 修复 Entity 与数据库 Schema 的版本漂移（schema drift）
-- 问题：Entity 新增了 project_root、opened_files、artifacts、last_opened_file 四个字段，
--       但 V16 迁移未被执行，且 project_root 和 last_opened_file 从未有过迁移脚本。
-- 使用 ADD COLUMN IF NOT EXISTS 确保幂等安全。
SET search_path TO mcp_agent;

-- 1. project_root: 项目根目录路径（Entity 已有，但从未有迁移）
ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS project_root VARCHAR(1000);

-- 2. opened_files: 已打开文件内容映射（V16 已定义，但可能未执行）
ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS opened_files JSONB;

-- 3. artifacts: Artifact 工作对象列表（V16 已定义，但可能未执行）← 本次报错字段
ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS artifacts JSONB;

-- 4. last_opened_file: 最后打开文件引用（Entity 已有，但从未有迁移）
ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS last_opened_file JSONB;

-- 补充注释
COMMENT ON COLUMN workspaces.project_root IS '项目根目录路径';
COMMENT ON COLUMN workspaces.opened_files IS '已打开文件内容映射 (Map<Path, OpenedFile{content, encoding, mtime, size}>)';
COMMENT ON COLUMN workspaces.artifacts IS 'Artifact 工作对象列表 — 与 Memory 完全解耦的临时可编辑对象';
COMMENT ON COLUMN workspaces.last_opened_file IS '最后打开的文件引用 (OpenedFile JSON)';