package com.mcp.tools.model;

/**
 * 工具的能力语义（Capability Semantics）—— 领域能力，非最终目标。
 * Planner 按能力需求选择工具，而非按工具名称。
 * 一个工具可以声明多个 Capability。
 * 命名原则：Planner 能直接理解、能准确映射到工具集合的领域能力。
 */
public enum ToolCapability {

    READ_FILE("读取文件", "读取文件内容、源码、配置等"),
    READ_PROJECT("读取项目结构", "读取项目树、模块结构、依赖关系等"),
    SEARCH_CODE("搜索代码", "全文搜索、正则搜索、语义搜索代码"),
    SEARCH_SYMBOL("搜索符号", "跳转定义、查找引用、类型层级"),
    EDIT_FILE("编辑文件", "修改、创建、删除代码或配置文件"),
    EXECUTE_COMMAND("执行命令", "运行终端命令、脚本执行"),
    MANAGE_FILES("管理文件", "文件系统操作：列表、移动、复制、删除"),
    FETCH_WEB("获取网络资源", "网页搜索、URL抓取、API调用"),
    ANALYZE_CODE("分析代码", "静态分析、Lint、诊断、代码审查"),
    PARSE_DOCUMENT("解析文档", "文档读取、解析、格式转换"),
    MANAGE_SYSTEM("管理系统", "系统级操作：环境变量、进程管理"),
    CUSTOM("自定义", "用户自定义能力");

    private final String displayName;
    private final String description;

    ToolCapability(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}