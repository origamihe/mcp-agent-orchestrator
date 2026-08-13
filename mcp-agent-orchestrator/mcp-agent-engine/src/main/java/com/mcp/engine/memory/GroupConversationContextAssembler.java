package com.mcp.engine.memory;

import com.mcp.common.artifact.GroupConversationContext;
import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.service.MemoryRetriever;
import com.mcp.engine.conversation.ConversationTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 群聊对话上下文组装器 — 统一编排分层上下文获取。
 *
 * 数据流：
 * ConversationTracker → Current Thread
 * GroupMemoryService  → Recent Group Context (按 createdAt)
 * MemoryRetriever     → Relevant Group Memory (语义检索)
 *
 * 职责：
 * - 协调多个数据源，构建分层 GroupConversationContext
 * - 不负责渲染 Prompt（由 ContextAssembler + PromptAssembly 负责）
 * - 不负责决策是否注入（由 PromptPolicy 负责）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupConversationContextAssembler {

    private final ConversationTracker conversationTracker;
    private final GroupMemoryService groupMemoryService;
    private final MemoryRetriever memoryRetriever;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final int RECENT_CONTEXT_LIMIT = 20;
    private static final int MEMORY_RETRIEVE_LIMIT = 5;

    /**
     * 组装群聊分层上下文。
     *
     * @param groupId   群ID
     * @param userId    当前用户ID
     * @param threadId  当前对话线程ID
     * @param userQuery 当前用户消息（用于语义检索）
     * @return 组装后的上下文
     */
    public GroupConversationContext assemble(String groupId, String userId,
                                              String threadId, String userQuery) {
        if (groupId == null || groupId.isEmpty()) {
            return GroupConversationContext.empty();
        }

        GroupConversationContext ctx = new GroupConversationContext(groupId, threadId);

        try {
            ctx.setCurrentThread(fetchCurrentThread(groupId, threadId));
        } catch (Exception e) {
            log.warn("[GroupConvAssembler] Failed to fetch current thread: groupId={}, threadId={}, error={}",
                    groupId, threadId, e.getMessage());
        }

        try {
            ctx.setRecentGroupContext(fetchRecentGroupContext(groupId));
        } catch (Exception e) {
            log.warn("[GroupConvAssembler] Failed to fetch recent group context: groupId={}, error={}",
                    groupId, e.getMessage());
        }

        try {
            ctx.setRelevantGroupMemory(fetchRelevantGroupMemory(groupId, userId, userQuery));
        } catch (Exception e) {
            log.warn("[GroupConvAssembler] Failed to fetch relevant group memory: groupId={}, error={}",
                    groupId, e.getMessage());
        }

        log.info("[GroupConvAssembler] Assembled context: groupId={}, threadId={}, layers={}, totalChars={}",
                groupId, threadId, ctx.layerCount(), ctx.toPromptText().length());
        return ctx;
    }

    /**
     * 获取当前线程消息（Current Thread）。
     * 从 ConversationTracker 获取 messageId 列表，再从 GroupMemoryService 回填内容。
     */
    private String fetchCurrentThread(String groupId, String threadId) {
        if (threadId == null) return null;

        List<String> messageIds = conversationTracker.getThreadMessageIds(threadId);
        if (messageIds.isEmpty()) return null;

        List<MemoryPackageEntity> messages = groupMemoryService.findByMessageIds(groupId, messageIds);
        if (messages.isEmpty()) return null;

        return messages.stream()
                .map(this::formatMessage)
                .collect(Collectors.joining("\n"));
    }

    /**
     * 获取最近群聊上下文（Recent Group Context）。
     * 严格按 createdAt 时间倒序，取最近 N 条。
     */
    private String fetchRecentGroupContext(String groupId) {
        List<MemoryPackageEntity> messages = groupMemoryService
                .getRecentMessagesByCreatedAt(groupId, RECENT_CONTEXT_LIMIT);
        if (messages.isEmpty()) return null;

        List<MemoryPackageEntity> chronological = messages.stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .toList();

        return chronological.stream()
                .map(this::formatMessage)
                .collect(Collectors.joining("\n"));
    }

    /**
     * 获取相关群聊记忆（Relevant Group Memory）。
     * 使用 MemoryRetriever 语义检索更早的群聊历史。
     */
    private String fetchRelevantGroupMemory(String groupId, String userId, String userQuery) {
        if (userQuery == null || userQuery.isEmpty()) return null;

        List<MemoryPackageEntity> results = memoryRetriever.retrieveForGroupContext(
                userId, groupId, userQuery, MEMORY_RETRIEVE_LIMIT);
        if (results.isEmpty()) return null;

        return results.stream()
                .map(this::formatMessage)
                .collect(Collectors.joining("\n"));
    }

    /**
     * 格式化单条消息为可读文本。
     */
    private String formatMessage(MemoryPackageEntity msg) {
        String senderName = extractSenderName(msg);
        String time = msg.getCreatedAt() != null
                ? "[" + msg.getCreatedAt().format(TIME_FORMATTER) + "]"
                : "";
        String content = msg.getContent() != null ? msg.getContent() : "";
        return time + " " + senderName + ": " + content;
    }

    private String extractSenderName(MemoryPackageEntity msg) {
        if (msg.getUserId() == null) return "未知";
        return msg.getUserId();
    }
}