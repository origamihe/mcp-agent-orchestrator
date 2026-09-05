package com.mcp.plugin.capability

data class CapabilityDef(
    val name: String,
    val description: String,
    val params: Map<String, String> = emptyMap()
)

/**
 * ⚠️ 同步提醒：此列表需要与 Gateway 侧的 CapabilityRiskRegistry.DEFAULT_RISK_MAP 保持同步。
 *    新增 capability 时，请同时更新：
 *    - 本文件 ALL_CAPABILITIES
 *    - CapabilityAdapter.kt execute() 方法
 *    - mcp-gateway/.../CapabilityRiskRegistry.java DEFAULT_RISK_MAP
 */
val ALL_CAPABILITIES = listOf(
    CapabilityDef("read_file", "读取指定文件内容", mapOf("filePath" to "string")),
    CapabilityDef("write_file", "写入文件内容", mapOf("filePath" to "string", "content" to "string")),
    CapabilityDef("read_directory", "读取目录结构", mapOf("path" to "string", "depth" to "int")),
    CapabilityDef("get_editor_state", "获取当前编辑器状态（光标、选中代码）", emptyMap()),
    CapabilityDef("get_open_files", "获取当前打开的文件列表", emptyMap()),
    CapabilityDef("get_diagnostics", "获取当前文件诊断信息", mapOf("filePath" to "string")),
    CapabilityDef("get_git_status", "获取 Git 状态", mapOf("path" to "string")),
    CapabilityDef("get_git_diff", "获取 Git diff", mapOf("path" to "string", "staged" to "boolean")),
    CapabilityDef("open_file", "在编辑器中打开文件", mapOf("filePath" to "string", "line" to "int")),
    CapabilityDef("search_files", "在项目中搜索文件", mapOf("pattern" to "string")),
    CapabilityDef("run_terminal", "执行终端命令", mapOf("command" to "string", "cwd" to "string", "_timeout" to "int", "_outputLimit" to "int")),
    CapabilityDef("apply_diff", "应用差异补丁到文件", mapOf("filePath" to "string", "diff" to "string")),
    CapabilityDef("apply_full_content", "覆盖文件完整内容", mapOf("filePath" to "string", "content" to "string"))
)