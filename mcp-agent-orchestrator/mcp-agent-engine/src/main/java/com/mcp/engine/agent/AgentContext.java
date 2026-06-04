package com.mcp.engine.agent;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class AgentContext {
    private String sessionId;
    private Map<String, Object> variables = new HashMap<>();
    private String memory;
}