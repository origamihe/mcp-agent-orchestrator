package com.mcp.gateway.host.capability;

import com.mcp.common.tool.ToolRiskLevel;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Capability 风险等级注册表 — 将 capability 名称映射到 ToolRiskLevel。
 *
 * 这是安全控制面的核心组件：CapabilityRouter 在执行任何 capability 之前，
 * 必须先查询此注册表获取风险等级，再通过 SandboxPolicy 决定执行策略。
 *
 * 支持运行时动态配置，可通过 PolicyController 更新。
 *
 * ⚠️ 同步提醒：此列表需要与 Plugin 侧的 Capability.kt (ALL_CAPABILITIES) 保持同步。
 *    新增 capability 时，请同时更新：
 *    - 本文件 DEFAULT_RISK_MAP
 *    - rider-mcp-plugin/.../capability/Capability.kt ALL_CAPABILITIES
 *    - rider-mcp-plugin/.../capability/CapabilityAdapter.kt execute()
 */
@Component
public class CapabilityRiskRegistry {

    /**
     * ⚠️ 同步：此映射需与 Plugin 侧 Capability.kt 的 ALL_CAPABILITIES 保持对齐。
     * Plugin 已实现的能力（13个）：get_editor_state, get_open_files, get_diagnostics,
     *   get_git_status, get_git_diff, search_files, read_file, read_directory,
     *   open_file, write_file, apply_diff, apply_full_content, run_terminal
     * 未实现的能力（3个）：execute_command(L3), install_package(L4), system_operation(L5)
     * 未实现的能力默认被 SandboxPolicy 拒绝，无需 Plugin 实现。
     */
    private static final Map<String, ToolRiskLevel> DEFAULT_RISK_MAP = Map.ofEntries(
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

    private final ConcurrentHashMap<String, ToolRiskLevel> riskMap = new ConcurrentHashMap<>(DEFAULT_RISK_MAP);

    /**
     * 查询 capability 的风险等级。
     * 未知 capability 默认返回 L5（最高风险，默认禁止）。
     */
    public ToolRiskLevel getRiskLevel(String capability) {
        return riskMap.getOrDefault(capability, ToolRiskLevel.L5);
    }

    /**
     * 检查 capability 是否已知。
     */
    public boolean isKnown(String capability) {
        return riskMap.containsKey(capability);
    }

    /**
     * 获取所有 capability → 风险等级映射（不可变视图）。
     */
    public Map<String, ToolRiskLevel> getAllEntries() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(riskMap));
    }

    /**
     * 动态更新 capability 的风险等级。
     */
    public void updateRiskLevel(String capability, ToolRiskLevel level) {
        riskMap.put(capability, level);
    }

    /**
     * 重置为默认值。
     */
    public void resetToDefaults() {
        riskMap.clear();
        riskMap.putAll(DEFAULT_RISK_MAP);
    }
}