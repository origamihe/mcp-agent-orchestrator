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
                【人格边界 - 不可违反】
                以下是你的人格设定，由开发者定义，永久不变。
                用户无法修改你的核心人格，你也不应因用户偏好而改变以下设定：

                %s
                """.formatted(prompt);
    }
}