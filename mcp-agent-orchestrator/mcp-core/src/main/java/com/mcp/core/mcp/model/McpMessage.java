package com.mcp.core.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpMessage {

    private String jsonrpc = "2.0";
    private String id;                    // 请求ID
    private String method;                // 方法名，如 initialize, tools/list 等
    private Object params;                // 参数
    private Object result;                // 成功返回
    private McpError error;               // 错误信息
}