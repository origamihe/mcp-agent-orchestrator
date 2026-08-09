-- V11: 为 chat_sessions 添加平台和群组身份字段
-- 支持 MemoryIdentity 的完整持久化（platform / userId / groupId）

ALTER TABLE chat_sessions
    ADD COLUMN platform VARCHAR(20),
    ADD COLUMN group_id VARCHAR(64);

-- 索引：支持按平台+用户ID联合查询
CREATE INDEX IF NOT EXISTS idx_session_platform_user
    ON chat_sessions(platform, user_id);

COMMENT ON COLUMN chat_sessions.platform IS '平台标识（qq / discord / telegram / web / wechat）';
COMMENT ON COLUMN chat_sessions.group_id IS '群组ID（群聊场景）';