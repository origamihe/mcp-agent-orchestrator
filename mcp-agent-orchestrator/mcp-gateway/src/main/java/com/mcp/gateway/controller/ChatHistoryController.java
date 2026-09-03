package com.mcp.gateway.controller;

import com.mcp.core.entity.ChatMessageEntity;
import com.mcp.core.service.ChatHistoryService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/mcp/chat-history")
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    public ChatHistoryController(ChatHistoryService chatHistoryService) {
        this.chatHistoryService = chatHistoryService;
    }

    @GetMapping("/sessions")
    public Mono<List<Map<String, Object>>> getSessions(
            @RequestParam(defaultValue = "default") String userId,
            @RequestParam(required = false) String agentId) {
        if (agentId != null && !agentId.isBlank()) {
            return chatHistoryService.getSessionsByAgentId(agentId);
        }
        return chatHistoryService.getAllSessions(userId);
    }

    @GetMapping("/{sessionId}")
    public Mono<List<Map<String, Object>>> getSessionMessages(@PathVariable String sessionId) {
        return chatHistoryService.getSessionMessages(sessionId)
                .map(messages -> messages.stream().map(m -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", m.getId());
                    map.put("sessionId", m.getSessionId());
                    map.put("role", m.getRole().name().toLowerCase());
                    map.put("content", m.getContent());
                    map.put("createdAt", m.getCreatedAt());
                    return map;
                }).collect(Collectors.toList()));
    }

    @DeleteMapping("/session/{sessionId}")
    public Mono<Map<String, Object>> deleteSession(@PathVariable String sessionId) {
        return chatHistoryService.deleteSession(sessionId)
                .thenReturn(Map.of("success", true, "message", "会话已删除"));
    }

    @DeleteMapping("/message/{messageId}")
    public Mono<Map<String, Object>> deleteMessage(@PathVariable Long messageId) {
        return chatHistoryService.deleteMessage(messageId)
                .thenReturn(Map.of("success", true, "message", "消息已删除"));
    }
}