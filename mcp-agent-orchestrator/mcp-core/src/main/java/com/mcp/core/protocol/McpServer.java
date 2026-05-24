package com.mcp.core.protocol;


/**
 * MCP Server（对外提供工具/数据的服务端）
 */
public interface McpServer {

    void start();

    void stop();

    // 注册工具、资源等
    void registerTool(Object tool);
}