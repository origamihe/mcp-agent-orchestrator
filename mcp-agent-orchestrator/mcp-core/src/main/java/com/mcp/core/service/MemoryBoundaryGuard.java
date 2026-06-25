package com.mcp.core.service;

import com.mcp.core.domain.memory.MemoryScope;
import com.mcp.core.entity.MemoryPackageEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 记忆边界守卫 - 确保 UserMemory 不会覆盖 Persona。
 *
 * 基于关键词匹配的轻量级检查。
 * 未来可扩展：接入 LLM 做语义级冲突检测。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryBoundaryGuard {

    private final PersonaMemoryStore personaMemoryStore;

    /**
     * 检查一条 UserMemory 是否与 Persona 定义冲突。
     */
    public boolean isConflictWithPersona(String userMemory) {
        String personaText = personaMemoryStore.getRawPersonaText();
        if (personaText.isEmpty()) return false;

        String lowerMemory = userMemory.toLowerCase();
        String lowerPersona = personaText.toLowerCase();

        // 规则1：如果用户记忆包含"主人"，而 Persona 明确禁止
        if (lowerMemory.contains("主人") || lowerMemory.contains("master")) {
            if (lowerPersona.contains("不叫") || lowerPersona.contains("不撒娇")
                    || lowerPersona.contains("不卖萌") || lowerPersona.contains("不过度")) {
                log.warn("[MemoryBoundary] 检测到冲突: 用户记忆要求'主人'称呼，但 Persona 禁止");
                return true;
            }
        }

        // 规则2：如果用户记忆试图定义 Bot 的性格
        if (lowerMemory.contains("你应该") || lowerMemory.contains("你要")
                || lowerMemory.contains("你的性格") || lowerMemory.contains("你的人格")) {
            log.warn("[MemoryBoundary] 检测到冲突: 用户记忆试图修改 Bot 人格");
            return true;
        }

        // 规则3：如果用户记忆包含与 Persona 相反的词
        if (lowerPersona.contains("冷淡") || lowerPersona.contains("不热情")) {
            if (lowerMemory.contains("撒娇") || lowerMemory.contains("卖萌")
                    || lowerMemory.contains("热情") || lowerMemory.contains("可爱")) {
                log.warn("[MemoryBoundary] 检测到冲突: 用户记忆要求的行为与 Persona 设定相反");
                return true;
            }
        }

        return false;
    }

    /**
     * 过滤 UserMemory 列表，移除与 Persona 冲突的条目。
     */
    public List<MemoryPackageEntity> filterConflicting(List<MemoryPackageEntity> userMemories) {
        return userMemories.stream()
                .filter(m -> m.getScope() != MemoryScope.PERSONA)
                .filter(m -> !isConflictWithPersona(m.getContent()))
                .toList();
    }
}