-- V21: 为 memory_packages 表添加 message_id 列
-- 用于 GroupMemoryService 记录群聊消息 ID，支持按 messageId 查询
SET search_path TO mcp_agent;

ALTER TABLE memory_packages
    ADD COLUMN IF NOT EXISTS message_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_memory_message_id ON memory_packages(message_id);
CREATE INDEX IF NOT EXISTS idx_memory_group_message_id ON memory_packages(group_id, message_id);

COMMENT ON COLUMN memory_packages.message_id IS '平台消息ID，用于群聊消息关联';