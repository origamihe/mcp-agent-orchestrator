package com.mcp.core.mcp.model;

import lombok.Data;

@Data
public class InitializeRequest {
    private String clientInfo;
    private String protocolVersion = "2024-11-05"; // MCP 协议版本
}