package com.mcp.core.service;

import com.mcp.common.identity.UserRole;
import com.mcp.core.domain.memory.MemoryScope;
import com.mcp.core.entity.MemoryPackageEntity;
import com.mcp.core.service.boundary.MemoryBoundaryRule;
import com.mcp.core.service.boundary.NicknameRule;
import com.mcp.core.service.boundary.PersonaModificationRule;
import com.mcp.core.service.boundary.BehaviorContradictionRule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 记忆边界守卫 - 确保 UserMemory 不会覆盖 Persona。
 *
 * 基于规则引擎（Rule Engine）架构：
 * - 每条规则独立判断一种冲突类型
 * - 规则支持权限覆盖：OWNER/ADMIN 可绕过非安全规则
 * - 新增规则只需实现 MemoryBoundaryRule 接口并注册
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryBoundaryGuard {

    private final PersonaMemoryStore personaMemoryStore;
    private final List<MemoryBoundaryRule> rules = new ArrayList<>();

    @PostConstruct
    public void init() {
        rules.add(new NicknameRule());
        rules.add(new PersonaModificationRule());
        rules.add(new BehaviorContradictionRule());
        log.info("[MemoryBoundary] 已注册 {} 条边界规则", rules.size());
    }

    /**
     * 检查一条 UserMemory 是否与 Persona 定义冲突（含身份权限检查）。
     *
     * 检查流程：
     * 1. 获取 Persona 文本
     * 2. 遍历所有规则，找到匹配的规则
     * 3. 对于匹配的规则，检查用户角色是否可以覆盖
     * 4. 如果角色不可覆盖，则判断冲突
     *
     * @param userMemory 用户记忆内容
     * @param userRole   用户角色（null 视为 MEMBER）
     * @return true 表示存在冲突且用户无权覆盖
     */
    public boolean isConflictWithPersona(String userMemory, UserRole userRole) {
        String personaText = personaMemoryStore.getRawPersonaText();
        if (personaText.isEmpty()) {
            return false;
        }

        UserRole effectiveRole = userRole != null ? userRole : UserRole.MEMBER;

        for (MemoryBoundaryRule rule : rules) {
            if (!rule.matches(userMemory, personaText)) {
                continue;
            }

            if (effectiveRole.isAtLeast(UserRole.OWNER)) {
                log.debug("[MemoryBoundary] 规则 '{}' 匹配，但用户角色为 OWNER，跳过所有检查", rule.name());
                return false;
            }

            UserRole overrideMinRole = rule.overrideMinimumRole();
            if (overrideMinRole != null && effectiveRole.isAtLeast(overrideMinRole)) {
                log.info("[MemoryBoundary] 规则 '{}' 匹配，但用户角色 {} 可覆盖（最低要求 {}），跳过",
                        rule.name(), effectiveRole, overrideMinRole);
                continue;
            }

            if (rule.isConflict(userMemory, personaText)) {
                log.warn("[MemoryBoundary] 规则 '{}' 检测到冲突: 用户记忆='{}'，角色={}",
                        rule.name(), userMemory, effectiveRole);
                return true;
            }
        }

        return false;
    }

    /**
     * 过滤 UserMemory 列表，移除与 Persona 冲突的条目（含身份权限检查）。
     *
     * @param userMemories 待过滤的用户记忆列表
     * @param userRole     用户角色（null 视为 MEMBER）
     * @return 过滤后的记忆列表
     */
    public List<MemoryPackageEntity> filterConflicting(List<MemoryPackageEntity> userMemories, UserRole userRole) {
        return userMemories.stream()
                .filter(m -> m.getScope() != MemoryScope.PERSONA)
                .filter(m -> !isConflictWithPersona(m.getContent(), userRole))
                .toList();
    }

    /**
     * 获取当前注册的规则数量（用于监控和测试）。
     */
    public int getRuleCount() {
        return rules.size();
    }
}