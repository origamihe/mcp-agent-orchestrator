-- V16: 为 workspaces 表添加 opened_files 和 artifacts 列
-- 持久化 Agent 已打开文件的内容与 Artifact 工作对象，解决 Follow-up 请求中文件内容丢失的问题
SET search_path TO mcp_agent;

ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS opened_files JSONB;

ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS artifacts JSONB;

COMMENT ON COLUMN workspaces.opened_files IS '已打开文件内容映射 (Map<Path, OpenedFile{content, encoding, mtime, size}>)';
COMMENT ON COLUMN workspaces.artifacts IS 'Artifact 工作对象列表 — 与 Memory 完全解耦的临时可编辑对象';