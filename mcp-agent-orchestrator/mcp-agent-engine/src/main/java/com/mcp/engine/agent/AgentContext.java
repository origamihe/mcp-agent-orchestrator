package com.mcp.engine.agent;

import com.mcp.common.identity.GroupContext;
import com.mcp.common.identity.UserProfile;
import com.mcp.common.identity.UserRole;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class AgentContext {
    private String sessionId;
    private String systemPrompt;
    private String developerPrompt;
    private String personaPrompt;
    private String groupContextPrompt;
    private String userProfilePrompt;
    private UserProfile userProfile;
    private GroupContext groupContext;
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();
    private String memory;

    public String getUserId() {
        return userProfile != null ? userProfile.getUserId() : null;
    }

    public String getGroupId() {
        return groupContext != null ? groupContext.getGroupId() : null;
    }

    public boolean isOwner() {
        return userProfile != null && userProfile.isOwner();
    }

    public boolean isAtLeast(UserRole role) {
        return userProfile != null && userProfile.isAtLeast(role);
    }

    /**
     * 构建分层 Prompt（按优先级排序）
     */
    public String buildLayeredPrompt() {
        StringBuilder sb = new StringBuilder();

        // 1. SYSTEM 层 - 安全规则
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            sb.append(systemPrompt).append("\n\n");
        }

        // 2. DEVELOPER 层 - 人格 + 行为规则 + 权限规则
        if (developerPrompt != null && !developerPrompt.isEmpty()) {
            sb.append(developerPrompt).append("\n\n");
        }

        // 3. PERSONA 层 - 澪音人格
        if (personaPrompt != null && !personaPrompt.isEmpty()) {
            sb.append(personaPrompt).append("\n\n");
        }

        // 4. GROUP CONTEXT 层 - 群设定
        if (groupContextPrompt != null && !groupContextPrompt.isEmpty()) {
            sb.append(groupContextPrompt).append("\n\n");
        }

        // 5. USER PROFILE 层 - 用户身份
        if (userProfilePrompt != null && !userProfilePrompt.isEmpty()) {
            sb.append(userProfilePrompt).append("\n\n");
        }

        // 6. MEMORY 层
        if (memory != null && !memory.isEmpty()) {
            sb.append(memory).append("\n\n");
        }

        return sb.toString().trim();
    }
}