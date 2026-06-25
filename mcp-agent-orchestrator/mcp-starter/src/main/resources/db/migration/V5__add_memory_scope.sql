-- V5: 添加 memory_scope 列，支持 PersonaMemory 与 UserMemory 物理隔离
ALTER TABLE mcp_agent.memory_packages
    ADD COLUMN IF NOT EXISTS scope VARCHAR(20) NOT NULL DEFAULT 'USER';

-- 为现有数据设置默认值
UPDATE mcp_agent.memory_packages SET scope = 'USER' WHERE scope IS NULL OR scope = '';

-- 创建索引以加速 scope 查询
CREATE INDEX IF NOT EXISTS idx_memory_packages_scope ON mcp_agent.memory_packages(scope);
CREATE INDEX IF NOT EXISTS idx_memory_packages_session_scope ON mcp_agent.memory_packages(session_id, scope);
CREATE INDEX IF NOT EXISTS idx_memory_packages_user_scope ON mcp_agent.memory_packages(user_id, scope);
CREATE INDEX IF NOT EXISTS idx_memory_packages_group_scope ON mcp_agent.memory_packages(group_id, scope);