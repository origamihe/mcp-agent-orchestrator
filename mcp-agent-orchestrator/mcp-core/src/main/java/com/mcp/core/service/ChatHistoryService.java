package com.mcp.core.service;

import com.mcp.common.identity.MemoryIdentity;
import com.mcp.core.domain.chat.CoreChatMessage;
import com.mcp.core.domain.chat.ChatSession;
import com.mcp.core.domain.chat.MessageRole;
import com.mcp.core.entity.ChatMessageEntity;
import com.mcp.core.entity.ChatSessionEntity;
import com.mcp.core.mapper.ChatMessageMapper;
import com.mcp.core.mapper.ChatSessionMapper;
import com.mcp.core.repository.ChatMessageRepository;
import com.mcp.core.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 聊天历史领域服务
 */
@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;

    /**
     * 获取会话历史摘要（用于 Prompt）
     */
    public Mono<String> getHistorySummary(String sessionId, int maxMessages) {
        return Mono.fromCallable(() -> {
            List<ChatMessageEntity> messages = messageRepository
                    .findTopNBySessionIdOrderByCreatedAtDesc(sessionId,
                            PageRequest.of(0, maxMessages));

            if (messages.isEmpty()) {
                return "";
            }

            return messages.stream()
                    .map(m -> m.getRole().getCode() + ": " + m.getContent())
                    .collect(Collectors.joining("\n"));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取会话历史摘要（带字符预算，用于 FastPath）
     *
     * 滑动窗口策略：
     * - 每条消息截断到 maxPerMessageChars（避免单条超长消息撑爆预算）
     * - 总字符数不超过 maxTotalChars（优先保留最近的消息）
     * - 消息按时间倒序加载，从最新开始累加，超出预算即停止
     *
     * @param sessionId          会话 ID
     * @param maxMessages        最多加载的消息条数
     * @param maxTotalChars      总字符预算上限
     * @param maxPerMessageChars 单条消息截断长度
     * @return 格式化的历史摘要字符串
     */
    public Mono<String> getHistorySummaryWithBudget(String sessionId, int maxMessages,
                                                     int maxTotalChars, int maxPerMessageChars) {
        return Mono.fromCallable(() -> {
            List<ChatMessageEntity> messages = messageRepository
                    .findTopNBySessionIdOrderByCreatedAtDesc(sessionId,
                            PageRequest.of(0, maxMessages));

            if (messages.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            int totalChars = 0;

            for (ChatMessageEntity m : messages) {
                String content = m.getContent();
                if (content == null) {
                    continue;
                }

                String truncated = content.length() > maxPerMessageChars
                        ? content.substring(0, maxPerMessageChars) + "...[truncated]"
                        : content;

                String line = m.getRole().getCode() + ": " + truncated;

                if (totalChars + line.length() > maxTotalChars) {
                    break;
                }

                if (sb.length() > 0) {
                    sb.insert(0, "\n");
                }
                sb.insert(0, line);
                totalChars += line.length();
            }

            return sb.toString();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 保存用户消息 + Assistant 回复
     */
    @Transactional
    public Mono<Void> saveUserAndAssistantMessage(MemoryIdentity identity, String userMessage, String assistantResponse) {
        String sessionId = identity.sessionId();
        return Mono.fromRunnable(() -> {
            sessionRepository.findById(sessionId)
                    .orElseGet(() -> {
                        ChatSessionEntity newSession = new ChatSessionEntity();
                        newSession.setSessionId(sessionId);
                        newSession.setUserId(identity.userId());
                        newSession.setPlatform(identity.platform());
                        newSession.setGroupId(identity.groupId());
                        return sessionRepository.save(newSession);
                    });

            ChatMessageEntity userMsg = new ChatMessageEntity();
            userMsg.setSessionId(sessionId);
            userMsg.setRole(MessageRole.USER);
            userMsg.setContent(userMessage);
            messageRepository.save(userMsg);

            ChatMessageEntity assistantMsg = new ChatMessageEntity();
            assistantMsg.setSessionId(sessionId);
            assistantMsg.setRole(MessageRole.ASSISTANT);
            assistantMsg.setContent(assistantResponse);
            messageRepository.save(assistantMsg);

            sessionRepository.updateLastActiveTime(sessionId, LocalDateTime.now());
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 获取完整会话历史
     */
    public Mono<ChatSession> getFullSession(String sessionId) {
        return Mono.fromCallable(() -> sessionRepository.findById(sessionId)
                .map(sessionMapper::toDomain)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取最近 N 条消息（领域对象）
     */
    public Mono<List<CoreChatMessage>> getRecentMessages(String sessionId, int limit) {
        return Mono.fromCallable(() -> messageRepository
                .findTopNBySessionIdOrderByCreatedAtDesc(sessionId, PageRequest.of(0, limit))
                .stream()
                .map(messageMapper::toDomain)
                .collect(Collectors.toList()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取会话全部消息（领域对象，按时间升序）
     */
    public Mono<List<CoreChatMessage>> getAllMessages(String sessionId) {
        return Mono.fromCallable(() -> messageRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(messageMapper::toDomain)
                .collect(Collectors.toList()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取所有会话列表（含消息数量）
     */
    public Mono<List<Map<String, Object>>> getAllSessions(String userId) {
        return Mono.fromCallable(() ->
                sessionRepository.findByUserIdOrderByLastActiveAtDesc(userId).stream().map(s -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("sessionId", s.getSessionId());
                    map.put("userId", s.getUserId());
                    map.put("platform", s.getPlatform());
                    map.put("agentId", s.getAgentId());
                    map.put("status", s.getStatus());
                    map.put("createdAt", s.getCreatedAt());
                    map.put("lastActiveAt", s.getLastActiveAt());
                    map.put("messageCount", messageRepository.countBySessionId(s.getSessionId()));
                    ChatMessageEntity firstMsg = messageRepository.findFirstBySessionIdOrderByCreatedAtAsc(s.getSessionId());
                    map.put("firstMessage", firstMsg != null ? firstMsg.getContent() : null);
                    return map;
                }).collect(Collectors.toList()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 根据 Agent ID 获取会话列表
     */
    public Mono<List<Map<String, Object>>> getSessionsByAgentId(String agentId) {
        return Mono.fromCallable(() ->
                sessionRepository.findByAgentIdOrderByLastActiveAtDesc(agentId).stream().map(s -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("sessionId", s.getSessionId());
                    map.put("userId", s.getUserId());
                    map.put("platform", s.getPlatform());
                    map.put("agentId", s.getAgentId());
                    map.put("status", s.getStatus());
                    map.put("createdAt", s.getCreatedAt());
                    map.put("lastActiveAt", s.getLastActiveAt());
                    map.put("messageCount", messageRepository.countBySessionId(s.getSessionId()));
                    return map;
                }).collect(Collectors.toList()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取会话的所有消息
     */
    public Mono<List<ChatMessageEntity>> getSessionMessages(String sessionId) {
        return Mono.fromCallable(() ->
                messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 删除会话及其所有消息
     */
    @Transactional
    public Mono<Void> deleteSession(String sessionId) {
        return Mono.fromRunnable(() -> {
            messageRepository.deleteBySessionId(sessionId);
            sessionRepository.deleteById(sessionId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 删除单条消息
     */
    @Transactional
    public Mono<Void> deleteMessage(Long messageId) {
        return Mono.fromRunnable(() ->
                messageRepository.deleteById(messageId))
                .subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 仅更新会话活跃时间，不保存消息（用于回顾类操作）
     */
    public Mono<Void> touchSession(String sessionId) {
        return Mono.fromRunnable(() ->
                sessionRepository.updateLastActiveTime(sessionId, LocalDateTime.now()))
                .subscribeOn(Schedulers.boundedElastic()).then();
    }
}