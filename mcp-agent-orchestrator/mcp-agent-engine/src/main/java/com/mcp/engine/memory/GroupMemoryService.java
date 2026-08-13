package com.mcp.engine.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.common.channel.ChannelMessage;
import com.mcp.core.domain.memory.MemoryCategory;
import com.mcp.core.domain.memory.MemoryScope;
import com.mcp.core.domain.memory.MemoryType;
import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.repository.MemoryPackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 群聊记忆服务 — 独立管理群聊消息的存储与检索。
 *
 * 与 UserMemory 的区别：
 * - UserMemory 在 Agent 回复后触发，经过 LLM 抽取/评估/合并
 * - GroupMemory 直接存储原始消息，异步非阻塞，不经过 LLM 处理
 *
 * 所有群聊消息（无论是否 @Agent）都会被记录，用于后续检索时提供群聊上下文。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupMemoryService {

    private final MemoryPackageRepository memoryPackageRepository;
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_RECENT_LIMIT = 50;

    /**
     * 异步记录群聊消息到 GroupMemory。
     * 不阻塞主流程，失败只记日志。
     */
    @Async
    public void recordMessage(ChannelMessage msg) {
        if (msg == null) return;
        if (msg.getChatType() != ChannelMessage.ChatType.GROUP) return;
        if (msg.getChatId() == null) return;

        try {
            MemoryPackageEntity entity = new MemoryPackageEntity();
            entity.setSessionId(msg.getPlatformSessionId());
            entity.setUserId(msg.getSenderId());
            entity.setGroupId(msg.getChatId());
            entity.setMessageId(msg.getMessageId());
            entity.setContent(msg.getContent() != null ? msg.getContent() : "");
            entity.setCategory(MemoryCategory.SUMMARY);
            entity.setScope(MemoryScope.GROUP);
            entity.setMemoryType(MemoryType.EVENT);
            entity.setVersion(1);
            entity.setAccessCount(0);
            entity.setWeight(1.0);
            entity.setImportance(20);
            entity.setConfidence(100);
            entity.setUpgradeCount(0);
            entity.setDecayRate(1.0);
            entity.setActive(true);
            entity.setMetadata(buildMetadata(msg));
            entity.setSourceQuote("group:" + msg.getChatId() + "|user:" + msg.getSenderId());

            memoryPackageRepository.save(entity);
            log.debug("[GroupMemory] 记录群聊消息 groupId={} userId={} msgId={} len={}",
                    msg.getChatId(), msg.getSenderId(), msg.getMessageId(),
                    msg.getContent() != null ? msg.getContent().length() : 0);
        } catch (Exception e) {
            log.warn("[GroupMemory] 记录群聊消息失败 groupId={} userId={}: {}",
                    msg.getChatId(), msg.getSenderId(), e.getMessage());
        }
    }

    /**
     * 获取群聊最近 N 条消息（按入库时间倒序）。
     * 原 getRecentMessages 按 weight 排序，无法保证时间顺序，已废弃。
     */
    public List<MemoryPackageEntity> getRecentMessages(String groupId, int limit) {
        return getRecentMessagesByCreatedAt(groupId, limit);
    }

    /**
     * 获取群聊最近 N 条消息（默认 50 条）。
     */
    public List<MemoryPackageEntity> getRecentMessages(String groupId) {
        return getRecentMessagesByCreatedAt(groupId, DEFAULT_RECENT_LIMIT);
    }

    /**
     * 获取群聊最近 N 条消息，严格按 createdAt 倒序。
     * 专用于 Recent Group Context 注入，确保时间连续性。
     */
    public List<MemoryPackageEntity> getRecentMessagesByCreatedAt(String groupId, int limit) {
        List<MemoryPackageEntity> all = memoryPackageRepository
                .findGroupMessagesByCreatedAtDesc(groupId);
        int n = limit > 0 ? limit : DEFAULT_RECENT_LIMIT;
        return all.stream()
                .limit(n)
                .toList();
    }

    /**
     * 根据 messageId 列表批量查询群聊消息内容。
     * 用于 ConversationTracker 的 Thread 消息内容回填。
     */
    public List<MemoryPackageEntity> findByMessageIds(String groupId, List<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) return List.of();
        return memoryPackageRepository.findByGroupIdAndMessageIdIn(groupId, messageIds);
    }

    /**
     * 构建消息元数据 JSON。
     */
    private String buildMetadata(ChannelMessage msg) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("messageId", msg.getMessageId());
        meta.put("senderName", msg.getSenderName());
        meta.put("mentionedUsers", msg.getMentionedUsers());
        meta.put("mentionedAgent", msg.isMentionedAgent());
        meta.put("replyToMessageId", msg.getReplyToMessageId());
        meta.put("recordedAt", LocalDateTime.now().toString());
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}