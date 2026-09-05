package com.mcp.tools.model;

import com.mcp.common.identity.MemoryIdentity;
import lombok.Data;
import java.util.Map;

@Data
public class ToolExecutionRequest {
    private String toolName;
    private Map<String, Object> arguments;
    private String requestId;
    private MemoryIdentity identity;
    private String agentId;
}