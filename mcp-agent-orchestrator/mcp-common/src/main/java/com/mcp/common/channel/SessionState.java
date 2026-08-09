package com.mcp.common.channel;

import java.time.Instant;

public class SessionState {
    private boolean voiceMode = false;
    private String language = "zh";
    private String pendingIntent;
    private Instant lastActiveAt = Instant.now();
    private AgentMode mode = AgentMode.CHAT;
    private RoleRuntime roleRuntime = null;
    private WorldState worldState = new WorldState();

    public SessionState() {}

    public boolean isVoiceMode() { return voiceMode; }
    public void setVoiceMode(boolean voiceMode) { this.voiceMode = voiceMode; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getPendingIntent() { return pendingIntent; }
    public void setPendingIntent(String pendingIntent) { this.pendingIntent = pendingIntent; }

    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    public AgentMode getMode() { return mode; }
    public void setMode(AgentMode mode) { this.mode = mode; }

    public boolean isGameMode() { return mode == AgentMode.GAME; }

    public RoleRuntime getRoleRuntime() { return roleRuntime; }
    public void setRoleRuntime(RoleRuntime roleRuntime) { this.roleRuntime = roleRuntime; }

    public WorldState getWorldState() { return worldState; }
    public void setWorldState(WorldState worldState) { this.worldState = worldState; }

    public void touch() { this.lastActiveAt = Instant.now(); }
}