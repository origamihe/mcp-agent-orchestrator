package com.mcp.tools.model;

import lombok.Data;
import java.util.Map;

@Data
public class ToolExecutionRequest {
    private String toolName;
    private Map<String, Object> arguments;
    private String requestId;
}