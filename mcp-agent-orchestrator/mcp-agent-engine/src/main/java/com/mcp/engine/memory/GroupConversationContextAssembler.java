package com.mcp.engine.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.common.artifact.GroupConversationContext;
import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.service.MemoryRetriever;
import com.mcp.engine.conversation.ConversationTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final int RECENT_CONTEXT_LIMIT = 20;
    private static final int MEMORY_RETRIEVE_LIMIT = 5;
    private static final double KEYWORD_RELEVANCE_THRESHOLD = 0.15;

    /**
     * 组装群聊分层上下文。
     *
     * @param groupId   群ID
     * @param userId    当前用户ID
     * @param threadId  当前对话线程ID
     * @param userQuery 当前用户消息（用于语义检索和相关性过滤）
     * @param botUserId Bot 在平台上的用户 ID，用于过滤 Agent 自己的消息（可为 null）
     * @return 组装后的上下文
     */
    public GroupConversationContext assemble(String groupId, String userId,
                                              String threadId, String userQuery,
                                              String botUserId) {
        if (groupId == null || groupId.isEmpty()) {
            return GroupConversationContext.empty();
        }

        GroupConversationContext ctx = new GroupConversationContext(groupId, threadId);

        // 先获取当前线程的 messageId 列表，用于后续去重
        List<String> threadMessageIds = (threadId != null)
                ? conversationTracker.getThreadMessageIds(threadId)
                : List.of();

        try {
            ctx.setCurrentThread(fetchCurrentThread(groupId, threadId));
        } catch (Exception e) {
            log.warn("[GroupConvAssembler] Failed to fetch current thread: groupId={}, threadId={}, error={}",
                    groupId, threadId, e.getMessage());
        }

        try {
            ctx.setRecentGroupContext(fetchRecentGroupContext(groupId, threadMessageIds, userQuery, botUserId));
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
     * 排除当前线程中已有的消息，避免重复注入导致 LLM 注意力偏移。
     * 排除 Agent 自己的消息，避免 LLM 混淆自己的历史回复与用户对话。
     * 对消息做轻量级关键词相关性评分，过滤与当前查询无关的消息。
     */
    private String fetchRecentGroupContext(String groupId, List<String> excludeMessageIds,
                                            String userQuery, String botUserId) {
        List<MemoryPackageEntity> messages = groupMemoryService
                .getRecentMessagesByCreatedAt(groupId, RECENT_CONTEXT_LIMIT);
        if (messages.isEmpty()) return null;

        List<MemoryPackageEntity> filtered = messages.stream()
                .filter(m -> m.getMessageId() != null
                        && !excludeMessageIds.contains(m.getMessageId()))
                .filter(m -> botUserId == null || !botUserId.equals(m.getUserId()))
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .toList();

        if (filtered.isEmpty()) return null;

        if (userQuery != null && !userQuery.isBlank()) {
            Set<String> keywords = extractKeywords(userQuery);
            if (!keywords.isEmpty()) {
                List<MemoryPackageEntity> relevant = filtered.stream()
                        .filter(m -> relevanceScore(m.getContent(), keywords) >= KEYWORD_RELEVANCE_THRESHOLD)
                        .toList();
                if (!relevant.isEmpty()) {
                    int dropped = filtered.size() - relevant.size();
                    if (dropped > 0) {
                        log.debug("[GroupConvAssembler] Keyword relevance filtered: {} dropped, {} kept",
                                dropped, relevant.size());
                    }
                    filtered = relevant;
                }
            }
        }

        if (filtered.isEmpty()) return null;

        return filtered.stream()
                .map(this::formatMessage)
                .collect(Collectors.joining("\n"));
    }

    /**
     * 从查询文本中提取关键词（中文分词简化版）。
     */
    private Set<String> extractKeywords(String query) {
        Set<String> keywords = new HashSet<>();
        String text = query.toLowerCase().trim();
        // 按常见分隔符切分
        String[] words = text.split("[\\s，。！？,.!?、；;：:（）()\\[\\]【】\"'\"'\\-_]+");
        for (String word : words) {
            word = word.trim();
            if (word.length() >= 2 && !isStopWord(word)) {
                keywords.add(word);
            }
        }
        // 对中文做 2-gram 分词
        if (text.length() >= 2) {
            for (int i = 0; i < text.length() - 1; i++) {
                String bigram = text.substring(i, i + 2);
                if (bigram.matches("[\u4e00-\u9fa5]{2}") && !isStopWord(bigram)) {
                    keywords.add(bigram);
                }
            }
        }
        return keywords;
    }

    /**
     * 计算消息内容与查询关键词的相关性得分。
     */
    private double relevanceScore(String content, Set<String> keywords) {
        if (content == null || content.isEmpty()) return 0.0;
        if (keywords.isEmpty()) return 1.0;
        String lower = content.toLowerCase();
        int hits = 0;
        for (String kw : keywords) {
            if (lower.contains(kw)) {
                hits++;
            }
        }
        return (double) hits / keywords.size();
    }

    /**
     * 简单停用词判断。
     */
    private boolean isStopWord(String word) {
        return word.length() <= 1
                || word.equals("的") || word.equals("了") || word.equals("是") || word.equals("在")
                || word.equals("我") || word.equals("你") || word.equals("他") || word.equals("她")
                || word.equals("它") || word.equals("们") || word.equals("这") || word.equals("那")
                || word.equals("不") || word.equals("也") || word.equals("就") || word.equals("都")
                || word.equals("和") || word.equals("与") || word.equals("或") || word.equals("但")
                || word.equals("而") || word.equals("及") || word.equals("让") || word.equals("把")
                || word.equals("被") || word.equals("从") || word.equals("到") || word.equals("对")
                || word.equals("向") || word.equals("给") || word.equals("为") || word.equals("以")
                || word.equals("要") || word.equals("会") || word.equals("能") || word.equals("可以")
                || word.equals("有") || word.equals("没") || word.equals("很") || word.equals("太")
                || word.equals("更") || word.equals("最") || word.equals("还") || word.equals("再")
                || word.equals("又") || word.equals("才") || word.equals("刚") || word.equals("已经")
                || word.equals("什么") || word.equals("怎么") || word.equals("哪") || word.equals("谁")
                || word.equals("吗") || word.equals("呢") || word.equals("吧") || word.equals("啊")
                || word.equals("the") || word.equals("a") || word.equals("an") || word.equals("is")
                || word.equals("are") || word.equals("was") || word.equals("were") || word.equals("be")
                || word.equals("to") || word.equals("of") || word.equals("in") || word.equals("for")
                || word.equals("on") || word.equals("with") || word.equals("at") || word.equals("by")
                || word.equals("this") || word.equals("that") || word.equals("it") || word.equals("and");
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
        // 优先从 metadata JSON 中提取 senderName（显示名）
        String senderName = extractSenderNameFromMetadata(msg);
        if (senderName != null && !senderName.isEmpty()) {
            return senderName;
        }
        // 退回到 userId
        if (msg.getUserId() == null) return "未知";
        return msg.getUserId();
    }

    /**
     * 从 metadata JSON 中提取 senderName。
     * metadata 格式：{"messageId":"...","senderName":"并非并非","mentionedUsers":[...],...}
     */
    private String extractSenderNameFromMetadata(MemoryPackageEntity msg) {
        if (msg.getMetadata() == null || msg.getMetadata().isEmpty()) return null;
        try {
            Map<String, Object> meta = objectMapper.readValue(
                    msg.getMetadata(), new TypeReference<Map<String, Object>>() {});
            Object senderName = meta.get("senderName");
            return senderName != null ? senderName.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}