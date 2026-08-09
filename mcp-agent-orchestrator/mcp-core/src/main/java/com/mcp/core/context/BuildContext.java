package com.mcp.core.context;

import com.mcp.common.channel.SessionState;
import com.mcp.common.channel.WorkingContext;
import com.mcp.common.identity.GroupContext;
import com.mcp.common.identity.UserProfile;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 构建上下文 — 承载构建 PromptContext 所需的所有原始输入。
 *
 * 设计原则：
 * - 纯数据对象，不包含任何逻辑
 * - 各 ContextProvider 按需读取自己关心的字段
 * - 未来新增字段时，不影响已有 Provider
 * - extensions 通用扩展槽：新增数据源时优先使用，避免修改核心类
 */
public class BuildContext {

    private final String baseSystemPrompt;
    private final String developerPrompt;
    private final String personaPrompt;
    private final String userMessage;
    private final UserProfile userProfile;
    private final GroupContext groupContext;
    private final SessionState state;
    private final WorkingContext workingContext;
    private final String workspacePrompt;
    private final String hostContextPrompt;
    private final Map<String, Object> extensions;

    private BuildContext(Builder builder) {
        this.baseSystemPrompt = builder.baseSystemPrompt;
        this.developerPrompt = builder.developerPrompt;
        this.personaPrompt = builder.personaPrompt;
        this.userMessage = builder.userMessage;
        this.userProfile = builder.userProfile;
        this.groupContext = builder.groupContext;
        this.state = builder.state;
        this.workingContext = builder.workingContext;
        this.workspacePrompt = builder.workspacePrompt;
        this.hostContextPrompt = builder.hostContextPrompt;
        this.extensions = builder.extensions;
    }

    public String baseSystemPrompt() { return baseSystemPrompt; }
    public String developerPrompt() { return developerPrompt; }
    public String personaPrompt() { return personaPrompt; }
    public String userMessage() { return userMessage; }
    public UserProfile userProfile() { return userProfile; }
    public GroupContext groupContext() { return groupContext; }
    public SessionState state() { return state; }
    public WorkingContext workingContext() { return workingContext; }
    public String workspacePrompt() { return workspacePrompt; }
    public String hostContextPrompt() { return hostContextPrompt; }
    public Map<String, Object> extensions() { return extensions; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String baseSystemPrompt;
        private String developerPrompt;
        private String personaPrompt;
        private String userMessage;
        private UserProfile userProfile;
        private GroupContext groupContext;
        private SessionState state;
        private WorkingContext workingContext;
        private String workspacePrompt;
        private String hostContextPrompt;
        private Map<String, Object> extensions = Collections.emptyMap();

        public Builder baseSystemPrompt(String v) { this.baseSystemPrompt = v; return this; }
        public Builder developerPrompt(String v) { this.developerPrompt = v; return this; }
        public Builder personaPrompt(String v) { this.personaPrompt = v; return this; }
        public Builder userMessage(String v) { this.userMessage = v; return this; }
        public Builder userProfile(UserProfile v) { this.userProfile = v; return this; }
        public Builder groupContext(GroupContext v) { this.groupContext = v; return this; }
        public Builder state(SessionState v) { this.state = v; return this; }
        public Builder workingContext(WorkingContext v) { this.workingContext = v; return this; }
        public Builder workspacePrompt(String v) { this.workspacePrompt = v; return this; }
        public Builder hostContextPrompt(String v) { this.hostContextPrompt = v; return this; }
        public Builder extensions(Map<String, Object> v) { this.extensions = v; return this; }
        public Builder extension(String key, Object value) {
            if (this.extensions.isEmpty()) {
                this.extensions = new HashMap<>();
            }
            this.extensions.put(key, value);
            return this;
        }

        public BuildContext build() { return new BuildContext(this); }
    }
}