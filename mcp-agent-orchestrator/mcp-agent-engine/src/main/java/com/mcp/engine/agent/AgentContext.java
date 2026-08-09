package com.mcp.engine.agent;

import com.mcp.common.identity.GroupContext;
import com.mcp.common.identity.UserProfile;
import com.mcp.common.identity.UserRole;
import com.mcp.common.channel.AgentMode;
import com.mcp.common.channel.RoleRuntime;
import com.mcp.common.channel.WorldState;
import com.mcp.engine.context.ContextBundle;
import com.mcp.engine.planner.EditPlan;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class AgentContext {
    private String sessionId;
    private String systemPrompt;
    private UserProfile userProfile;
    private GroupContext groupContext;
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();
    private EditPlan editPlan;
    private ContextBundle contextBundle;
    private ExecutionTracker executionTracker;
    @Builder.Default
    private AgentMode mode = AgentMode.CHAT;
    @Builder.Default
    private RoleRuntime roleRuntime = null;
    @Builder.Default
    private WorldState worldState = new WorldState();

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


}