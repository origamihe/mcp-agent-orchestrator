package com.mcp.core.service.boundary;

import com.mcp.common.identity.UserRole;

/**
 * 昵称规则 - 检测用户记忆是否要求使用 Persona 禁止的称呼。
 *
 * 例如：用户记忆要求"叫我主人"，但 Persona 设定为"不叫主人"。
 * OWNER 和 ADMIN 可以覆盖此规则。
 */
public class NicknameRule implements MemoryBoundaryRule {

    private static final String[] NICKNAME_KEYWORDS = {"主人", "master", "老公", "老婆",
            "哥哥", "姐姐", "爸爸", "妈妈", "男朋友", "女朋友", "亲爱的", "宝贝"};

    private static final String[] PERSONA_FORBID_KEYWORDS = {"不叫", "不撒娇", "不卖萌",
            "不过度", "禁止", "不允许", "不能叫", "不要叫", "不可以叫"};

    @Override
    public String name() {
        return "NicknameRule";
    }

    @Override
    public boolean matches(String userMemory, String personaText) {
        String lowerMemory = userMemory.toLowerCase();
        for (String keyword : NICKNAME_KEYWORDS) {
            if (lowerMemory.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isConflict(String userMemory, String personaText) {
        String lowerPersona = personaText.toLowerCase();
        for (String forbidKeyword : PERSONA_FORBID_KEYWORDS) {
            if (lowerPersona.contains(forbidKeyword)) {
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