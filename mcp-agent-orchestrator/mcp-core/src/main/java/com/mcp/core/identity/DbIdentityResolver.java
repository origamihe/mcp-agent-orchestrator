package com.mcp.core.identity;

import com.mcp.common.identity.DefaultIdentityResolver;
import com.mcp.common.identity.IdentityResolver;
import com.mcp.common.identity.MemoryIdentity;
import com.mcp.core.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 数据库优先的身份解析器。
 *
 * 解析策略：
 *   1. 优先查询 ChatSessionEntity（含 platform/userId/groupId）
 *   2. 若 DB 无记录，回退到 DefaultIdentityResolver（sessionId 字符串解析）
 *
 * 优势：
 *   - 新会话写入 DB 后，后续解析零开销
 *   - 旧数据兼容：sessionId 仍可解析
 *   - 未来可扩展：加索引、缓存等
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class DbIdentityResolver implements IdentityResolver {

    private final ChatSessionRepository sessionRepository;
    private final DefaultIdentityResolver fallback;

    @Override
    public MemoryIdentity resolve(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return fallback.resolve(sessionId);
        }

        return sessionRepository.findById(sessionId)
                .map(entity -> {
                    MemoryIdentity identity = new MemoryIdentity(
                            entity.getPlatform(),
                            entity.getSessionId(),
                            entity.getUserId(),
                            entity.getGroupId(),
                            null
                    );
                    log.debug("[DbIdentity] 从 DB 解析: sessionId={} platform={} userId={} groupId={}",
                            sessionId, identity.platform(), identity.userId(), identity.groupId());
                    return identity;
                })
                .orElseGet(() -> {
                    log.debug("[DbIdentity] DB 无记录，回退到字符串解析: sessionId={}", sessionId);
                    return fallback.resolve(sessionId);
                });
    }
}