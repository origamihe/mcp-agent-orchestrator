package com.mcp.common.channel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 世界状态 — 为跑团、NPC、长期任务维护独立的世界上下文。
 * Agent 始终从 World → Role → Memory 读取，而非仅依赖聊天历史。
 */
public class WorldState {

    private String currentTime;
    private String currentLocation;
    private String weather;
    private String atmosphere;
    private List<String> presentNpcs = new ArrayList<>();
    private List<String> activeEvents = new ArrayList<>();
    private Map<String, String> worldRules = new LinkedHashMap<>();
    private List<String> recentHappenings = new ArrayList<>();

    public WorldState() {}

    public String getCurrentTime() { return currentTime; }
    public void setCurrentTime(String currentTime) { this.currentTime = currentTime; }

    public String getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(String currentLocation) { this.currentLocation = currentLocation; }

    public String getWeather() { return weather; }
    public void setWeather(String weather) { this.weather = weather; }

    public String getAtmosphere() { return atmosphere; }
    public void setAtmosphere(String atmosphere) { this.atmosphere = atmosphere; }

    public List<String> getPresentNpcs() { return presentNpcs; }
    public void setPresentNpcs(List<String> presentNpcs) { this.presentNpcs = presentNpcs; }

    public List<String> getActiveEvents() { return activeEvents; }
    public void setActiveEvents(List<String> activeEvents) { this.activeEvents = activeEvents; }

    public Map<String, String> getWorldRules() { return worldRules; }
    public void setWorldRules(Map<String, String> worldRules) { this.worldRules = worldRules; }

    public List<String> getRecentHappenings() { return recentHappenings; }
    public void setRecentHappenings(List<String> recentHappenings) { this.recentHappenings = recentHappenings; }

    public boolean isEmpty() {
        return currentTime == null
                && currentLocation == null
                && weather == null
                && atmosphere == null
                && presentNpcs.isEmpty()
                && activeEvents.isEmpty()
                && worldRules.isEmpty()
                && recentHappenings.isEmpty();
    }

    /**
     * 将世界状态转换为 Prompt 片段。
     * 由 PromptComposer 在构建分层 Prompt 时调用。
     */
    public String buildWorldPrompt() {
        if (isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("【世界状态】\n");

        if (currentTime != null) {
            sb.append("当前时间：").append(currentTime).append("\n");
        }
        if (currentLocation != null) {
            sb.append("当前位置：").append(currentLocation).append("\n");
        }
        if (weather != null) {
            sb.append("天气：").append(weather).append("\n");
        }
        if (atmosphere != null) {
            sb.append("氛围：").append(atmosphere).append("\n");
        }

        if (!presentNpcs.isEmpty()) {
            sb.append("在场NPC：").append(String.join("、", presentNpcs)).append("\n");
        }
        if (!activeEvents.isEmpty()) {
            sb.append("进行中的事件：").append(String.join("、", activeEvents)).append("\n");
        }
        if (!worldRules.isEmpty()) {
            sb.append("世界规则：\n");
            worldRules.forEach((k, v) -> sb.append("  - ").append(k).append("：").append(v).append("\n"));
        }
        if (!recentHappenings.isEmpty()) {
            sb.append("最近发生：\n");
            recentHappenings.forEach(h -> sb.append("  - ").append(h).append("\n"));
        }

        return sb.toString();
    }
}