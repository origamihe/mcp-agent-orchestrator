package com.mcp.gateway.host.capability;

import com.mcp.common.tool.ToolRiskLevel;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Capability 风险等级注册表 — 将 capability 名称映射到 ToolRiskLevel。
 *
 * 这是安全控制面的核心组件：CapabilityRouter 在执行任何 capability 之前，
 * 必须先查询此注册表获取风险等级，再通过 SandboxPolicy 决定执行策略。
 */
@Component
public class CapabilityRiskRegistry {

    private static final Map<String, ToolRiskLevel> RISK_MAP = Map.ofEntries(
            Map.entry("get_editor_state", ToolRiskLevel.L0),
            Map.entry("get_open_files", ToolRiskLevel.L0),
            Map.entry("get_diagnostics", ToolRiskLevel.L1),
            Map.entry("get_git_status", ToolRiskLevel.L1),
            Map.entry("get_git_diff", ToolRiskLevel.L1),
            Map.entry("search_files", ToolRiskLevel.L1),
            Map.entry("read_file", ToolRiskLevel.L1),
            Map.entry("read_directory", ToolRiskLevel.L1),
            Map.entry("open_file", ToolRiskLevel.L1),
            Map.entry("write_file", ToolRiskLevel.L2),
            Map.entry("apply_diff", ToolRiskLevel.L2),
            Map.entry("apply_full_content", ToolRiskLevel.L2),
            Map.entry("run_terminal", ToolRiskLevel.L3),
            Map.entry("execute_command", ToolRiskLevel.L3),
            Map.entry("install_package", ToolRiskLevel.L4),
            Map.entry("system_operation", ToolRiskLevel.L5)
    );

    /**
     * 查询 capability 的风险等级。
     * 未知 capability 默认返回 L5（最高风险，默认禁止）。
     */
    public ToolRiskLevel getRiskLevel(String capability) {
        return RISK_MAP.getOrDefault(capability, ToolRiskLevel.L5);
    }

    /**
     * 检查 capability 是否已知。
     */
    public boolean isKnown(String capability) {
        return RISK_MAP.containsKey(capability);
    }
}