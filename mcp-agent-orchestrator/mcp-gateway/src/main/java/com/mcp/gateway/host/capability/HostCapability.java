package com.mcp.gateway.host.capability;

import java.util.Map;

/**
 * Host 能力定义 — 插件向 Gateway 声明自己能做什么。
 * Agent 通过 Tool 调用这些能力，插件按需执行并返回结果。
 */
public class HostCapability {

    private String name;             // 能力名称，如 "read_file", "list_directory"
    private String description;      // 能力描述
    private Map<String, String> params; // 参数定义 (paramName -> type)

    public HostCapability() {}

    public HostCapability(String name, String description, Map<String, String> params) {
        this.name = name;
        this.description = description;
        this.params = params;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, String> getParams() { return params; }
    public void setParams(Map<String, String> params) { this.params = params; }
}