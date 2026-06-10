package com.mcp.core.service;

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
        });
    }

    /**
     * 保存用户消息 + Assistant 回复
     */
    @Transactional
    public Mono<Void> saveUserAndAssistantMessage(String sessionId, String userMessage, String assistantResponse) {
        return Mono.fromRunnable(() -> {
            // 确保会话存在（不存在则创建）
            sessionRepository.findById(sessionId)
                    .orElseGet(() -> {
                        ChatSessionEntity newSession = new ChatSessionEntity();
                        newSession.setSessionId(sessionId);
                        newSession.setUserId("default-user");
                        return sessionRepository.save(newSession);
                    });

            // 保存用户消息
            ChatMessageEntity userMsg = new ChatMessageEntity();
            userMsg.setSessionId(sessionId);
            userMsg.setRole(MessageRole.USER);
            userMsg.setContent(userMessage);
            messageRepository.save(userMsg);

            // 保存 Assistant 消息
            ChatMessageEntity assistantMsg = new ChatMessageEntity();
            assistantMsg.setSessionId(sessionId);
            assistantMsg.setRole(MessageRole.ASSISTANT);
            assistantMsg.setContent(assistantResponse);
            messageRepository.save(assistantMsg);

            // 更新会话最后活跃时间
            sessionRepository.updateLastActiveTime(sessionId, LocalDateTime.now());
        });
    }

    /**
     * 获取完整会话历史
     */
    public Mono<ChatSession> getFullSession(String sessionId) {
        return Mono.fromCallable(() -> sessionRepository.findById(sessionId)
                .map(sessionMapper::toDomain)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId)));
    }

    /**
     * 获取最近 N 条消息（领域对象）
     */
    public Mono<List<CoreChatMessage>> getRecentMessages(String sessionId, int limit) {
        return Mono.fromCallable(() -> messageRepository
                .findTopNBySessionIdOrderByCreatedAtDesc(sessionId, PageRequest.of(0, limit))
                .stream()
                .map(messageMapper::toDomain)
                .collect(Collectors.toList()));
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
                    map.put("createdAt", s.getCreatedAt());
                    map.put("lastActiveAt", s.getLastActiveAt());
                    map.put("messageCount", messageRepository.countBySessionId(s.getSessionId()));
                    ChatMessageEntity firstMsg = messageRepository.findFirstBySessionIdOrderByCreatedAtAsc(s.getSessionId());
                    map.put("firstMessage", firstMsg != null ? firstMsg.getContent() : null);
                    return map;
                }).collect(Collectors.toList()));
    }

    /**
     * 获取会话的所有消息
     */
    public Mono<List<ChatMessageEntity>> getSessionMessages(String sessionId) {
        return Mono.fromCallable(() ->
                messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId));
    }

    /**
     * 删除会话及其所有消息
     */
    @Transactional
    public Mono<Void> deleteSession(String sessionId) {
        return Mono.fromRunnable(() -> {
            messageRepository.deleteBySessionId(sessionId);
            sessionRepository.deleteById(sessionId);
        });
    }

    /**
     * 删除单条消息
     */
    @Transactional
    public Mono<Void> deleteMessage(Long messageId) {
        return Mono.fromRunnable(() ->
                messageRepository.deleteById(messageId));
    }
}