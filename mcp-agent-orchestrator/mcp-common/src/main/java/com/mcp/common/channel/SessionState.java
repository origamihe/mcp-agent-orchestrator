package com.mcp.common.channel;

import java.time.Instant;

public class SessionState {
    private boolean voiceMode = true;
    private String language = "ja";
    private String pendingIntent;
    private Instant lastActiveAt = Instant.now();

    public SessionState() {}

    public boolean isVoiceMode() { return voiceMode; }
    public void setVoiceMode(boolean voiceMode) { this.voiceMode = voiceMode; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getPendingIntent() { return pendingIntent; }
    public void setPendingIntent(String pendingIntent) { this.pendingIntent = pendingIntent; }

    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    public void touch() { this.lastActiveAt = Instant.now(); }
}