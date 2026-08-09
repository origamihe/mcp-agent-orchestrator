package com.mcp.core.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Persona 记忆存储 - 纯内存，从 YAML 配置加载，不依赖数据库。
 *
 * 设计原则：
 * 1. Persona 从 YAML 配置加载，纯内存存储，不写数据库（避免外键约束）
 * 2. Persona 是 code-defined，不是 data-driven——无需数据库持久化
 * 3. 这是抵御人格漂移的最后一道防线——代码级保证，不是 prompt 级建议
 */
@Slf4j
@Service
public class PersonaMemoryStore {

    @Value("${channel.qq.system-prompt}")
    private String personaPrompt;

    private String cachedPersonaText;

    @PostConstruct
    public void init() {
        if (personaPrompt == null || personaPrompt.isBlank()) {
            log.warn("[PersonaMemory] 未配置 channel.qq.system-prompt，Persona 记忆为空");
            cachedPersonaText = "";
        } else {
            cachedPersonaText = buildPersonaText(personaPrompt);
            log.info("[PersonaMemory] 已加载，{} 字符", personaPrompt.length());
        }
    }

    /**
     * 获取 Persona 记忆的纯文本，用于注入 System Prompt。
     */
    public String getPersonaMemoryText() {
        return cachedPersonaText;
    }

    /**
     * 获取原始 Persona 文本（用于 MemoryBoundaryGuard 做冲突检测）。
     */
    public String getRawPersonaText() {
        return personaPrompt != null ? personaPrompt : "";
    }

    private String buildPersonaText(String prompt) {
        return """
                【核心身份】
                以下是你的人格设定，是你的身份基础，不是临时角色。
                保持人格一致性是你的首要目标：

                %s

                【行为准则】
                回答问题时，按以下优先级利用信息：
                ① 当前对话上下文
                ② Session Memory（本次会话中已发生的事）
                ③ 长期记忆（历史对话中记录的偏好、关系、事件）
                若以上三者均不足以回答，再向用户确认。
                """.formatted(prompt);
    }
}