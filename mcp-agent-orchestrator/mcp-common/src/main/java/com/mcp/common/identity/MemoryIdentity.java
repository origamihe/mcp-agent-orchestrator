package com.mcp.common.identity;

/**
 * 记忆身份标识 - 纯 DTO，不包含任何解析逻辑。
 * 由 IdentityResolver 或 ChannelOrchestrator 负责构建。
 *
 * 设计原则：
 * - 零解析逻辑（SRP）
 * - 新增字段时调用方 API 不变
 * - platform 用于区分跨平台同名 userId/groupId
 * - workspaceId 预留多工作空间支持
 */
public record MemoryIdentity(
        String platform,
        String sessionId,
        String userId,
        String groupId,
        String workspaceId
) {
    public boolean hasUserId() {
        return userId != null && !userId.isBlank();
    }

    public boolean hasGroupId() {
        return groupId != null && !groupId.isBlank();
    }
}