package com.mcp.llm.client;

/**
 * 消息类型 - 用于 Memory 过滤和对话结构化。
 *
 * <p>
 * 语义说明：
 * <ul>
 *   <li>{@link #NORMAL} - 正常对话内容，用户输入或助理回复</li>
 *   <li>{@link #SYSTEM} - 系统提示词，不应存入 Memory</li>
 *   <li>{@link #PLAN} - 助理的规划/执行计划（如"我将使用XX工具"），不应存入 Memory</li>
 *   <li>{@link #TOOL} - 工具调用请求或工具返回结果，不应存入 Memory</li>
 *   <li>{@link #SUMMARY} - 助理的最终总结/研究报告，应存入 Memory</li>
 *   <li>{@link #TEMPLATE} - 回复模板/框架（如"【核心发现】...【争议分析】..."），不应存入 Memory</li>
 * </ul>
 */
public enum MessageType {
    NORMAL,
    SYSTEM,
    PLAN,
    TOOL,
    SUMMARY,
    TEMPLATE
}