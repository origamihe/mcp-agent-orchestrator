package com.mcp.engine.reflection;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearningBudgetManager {

    private final Map<String, Integer> sessionReflectionCount = new ConcurrentHashMap<>();
    private final Map<String, Long> lastReflectionTime = new ConcurrentHashMap<>();

    private static final int MAX_REFLECTIONS_PER_SESSION = 3;
    private static final long MIN_INTERVAL_MS = 30_000; // 30 秒

    public boolean shouldReflect(String sessionId, String userRequest) {
        // 规则 1: 简单闲聊不触发
        if (isSimpleChat(userRequest)) {
            return false;
        }

        // 规则 2: 每会话最多 3 次 Reflection
        int count = sessionReflectionCount.getOrDefault(sessionId, 0);
        if (count >= MAX_REFLECTIONS_PER_SESSION) {
            log.debug("[LearningBudget] Session {} 已达上限 {} 次", sessionId, MAX_REFLECTIONS_PER_SESSION);
            return false;
        }

        // 规则 3: 两次 Reflection 间隔至少 30 秒
        Long lastTime = lastReflectionTime.get(sessionId);
        if (lastTime != null && System.currentTimeMillis() - lastTime < MIN_INTERVAL_MS) {
            log.debug("[LearningBudget] Session {} 间隔不足 {}ms", sessionId, MIN_INTERVAL_MS);
            return false;
        }

        return true;
    }

    public void recordReflection(String sessionId) {
        sessionReflectionCount.merge(sessionId, 1, Integer::sum);
        lastReflectionTime.put(sessionId, System.currentTimeMillis());
    }

    private boolean isSimpleChat(String request) {
        if (request == null) return true;
        String lower = request.toLowerCase().trim();
        return lower.length() < 10
                || lower.matches("^(你好|hi|hello|谢谢|再见|好的|嗯|哦|哈哈).*")
                || !lower.contains(" ") && lower.length() < 8;
    }
}