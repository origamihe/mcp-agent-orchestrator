package com.mcp.engine.agent.card;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Agent 能力卡片（A2A 协议 - Agent Discovery）
 * <p>
 * 每个 Agent 通过 AgentCard 自描述其能力，供 Orchestrator 进行任务路由。
 * 参考 Google A2A 协议规范。
 */
@Data
@Builder
public class AgentCard {

    private String agentId;

    private String agentName;

    private String description;

    private AgentType agentType;

    private List<String> skills;

    private List<String> toolNames;

    private Map<String, String> inputSchema;

    private Map<String, String> outputSchema;

    private boolean supportsStreaming;

    private int maxConcurrentTasks;

    private String version;

    private String promptName;

    private String modelName;

    public enum AgentType {
        CHAT,
        CODE,
        SEARCH,
        DOCUMENT,
        PLANNER,
        EXECUTOR,
        GENERAL
    }
}