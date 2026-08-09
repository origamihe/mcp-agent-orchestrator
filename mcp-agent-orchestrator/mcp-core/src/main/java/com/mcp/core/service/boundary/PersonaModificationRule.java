package com.mcp.core.service.boundary;

import com.mcp.common.identity.UserRole;

/**
 * 人格修改规则 - 检测用户记忆是否试图直接修改 Bot 的人格设定。
 *
 * 例如：用户记忆包含"你应该xxx"、"你的性格是xxx"等试图定义 Bot 行为的语句。
 * 只有 OWNER 可以覆盖此规则。
 */
public class PersonaModificationRule implements MemoryBoundaryRule {

    private static final String[] MODIFICATION_KEYWORDS = {
            "你应该", "你要", "你必须", "你应当", "你该",
            "你的性格", "你的人格", "你的设定", "你的身份",
            "从现在起你是", "以后你是", "你的角色是", "你扮演"
    };

    @Override
    public String name() {
        return "PersonaModificationRule";
    }

    @Override
    public boolean matches(String userMemory, String personaText) {
        String lowerMemory = userMemory.toLowerCase();
        for (String keyword : MODIFICATION_KEYWORDS) {
            if (lowerMemory.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isConflict(String userMemory, String personaText) {
        return true;
    }

    @Override
    public UserRole overrideMinimumRole() {
        return UserRole.OWNER;
    }
}