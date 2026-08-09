package com.mcp.tools.model;

public enum ToolCategory {

    READ("读取", "读取文件、代码、配置等"),
    WRITE("写入", "修改、创建、删除文件"),
    SEARCH("搜索", "全文搜索、符号搜索、引用搜索"),
    SYMBOL("符号", "代码符号查找、导航"),
    WEB("网络", "网页搜索、抓取、API调用"),
    FILE("文件", "文件系统操作"),
    CODE("代码", "代码生成、重构、格式化"),
    DOCUMENT("文档", "文档读取、解析、转换"),
    SYSTEM("系统", "系统命令、环境操作"),
    CUSTOM("自定义", "用户自定义工具");

    private final String displayName;
    private final String description;

    ToolCategory(String displayName, String description) {
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