package com.mcp.common.identity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 默认身份解析器 - 从 sessionId 命名规则构建 MemoryIdentity。
 *
 * 当前实现：字符串解析（临时方案）。
 * 后续可替换为 DbIdentityResolver，通过 ChatSessionEntity 查询。
 *
 * sessionId 格式约定（由 ChannelAdapter 生成）：
 *   qq-private-{userId}  → 私聊
 *   qq-group-{groupId}   → 群聊
 */
@Slf4j
@Service
public class DefaultIdentityResolver implements IdentityResolver {

    @Override
    public MemoryIdentity resolve(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return new MemoryIdentity(null, sessionId, null, null, null);
        }
        int lastDash = sessionId.lastIndexOf('-');
        if (lastDash < 0) {
            return new MemoryIdentity(null, sessionId, null, null, null);
        }
        String prefix = sessionId.substring(0, lastDash);
        String id = sessionId.substring(lastDash + 1);

        if (prefix.endsWith("-private") || prefix.endsWith("-user")) {
            return new MemoryIdentity(extractPlatform(prefix), sessionId, id, null, null);
        }
        if (prefix.endsWith("-group")) {
            return new MemoryIdentity(extractPlatform(prefix), sessionId, null, id, null);
        }
        return new MemoryIdentity(extractPlatform(prefix), sessionId, null, null, null);
    }

    private String extractPlatform(String prefix) {
        if (prefix.startsWith("qq-")) return "qq";
        if (prefix.startsWith("discord-")) return "discord";
        if (prefix.startsWith("tg-")) return "telegram";
        return prefix;
    }
}