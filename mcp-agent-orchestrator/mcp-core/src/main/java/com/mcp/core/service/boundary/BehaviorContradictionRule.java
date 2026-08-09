package com.mcp.core.service.boundary;

import com.mcp.common.identity.UserRole;

/**
 * 行为矛盾规则 - 检测用户记忆要求的行为是否与 Persona 设定明确相反。
 *
 * 例如：Persona 设定为"冷淡、不热情"，但用户记忆要求"撒娇、卖萌"。
 * ADMIN 及以上可以覆盖此规则。
 */
public class BehaviorContradictionRule implements MemoryBoundaryRule {

    private static final String[] PERSONA_COLD_KEYWORDS = {"冷淡", "不热情", "高冷", "冷漠", "严肃"};

    private static final String[] MEMORY_WARM_KEYWORDS = {"撒娇", "卖萌", "热情", "可爱",
            "温柔", "活泼", "粘人", "贴心"};

    @Override
    public String name() {
        return "BehaviorContradictionRule";
    }

    @Override
    public boolean matches(String userMemory, String personaText) {
        String lowerPersona = personaText.toLowerCase();
        for (String coldKeyword : PERSONA_COLD_KEYWORDS) {
            if (lowerPersona.contains(coldKeyword)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isConflict(String userMemory, String personaText) {
        String lowerMemory = userMemory.toLowerCase();
        for (String warmKeyword : MEMORY_WARM_KEYWORDS) {
            if (lowerMemory.contains(warmKeyword)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public UserRole overrideMinimumRole() {
        return UserRole.ADMIN;
    }
}