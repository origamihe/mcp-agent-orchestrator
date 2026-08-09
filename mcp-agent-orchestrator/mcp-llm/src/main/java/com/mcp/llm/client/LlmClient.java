package com.mcp.llm.client;

import com.mcp.core.domain.chat.CoreChatMessage;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * LLM 客户端统一抽象接口
 */
public interface LlmClient {

    Mono<String> generate(String prompt);

    Mono<String> generateWithSystemPrompt(String systemPrompt, String userPrompt);

    Mono<String> chat(String message);

    /**
     * 带历史对话的聊天（推荐使用）
     */
    Mono<String> chatWithHistory(List<ChatMessage> history, String newMessage);

    /**
     * 使用 Core Domain 的历史（内部推荐）
     */
    Mono<String> chatWithCoreHistory(List<CoreChatMessage> history, String newMessage);

    /**
     * 使用指定模型配置生成回复
     */
    Mono<String> generateWithConfig(String configId, String prompt);

    /**
     * 使用指定模型配置 + 系统提示生成回复
     */
    Mono<String> generateWithConfigAndSystem(String configId, String systemPrompt, String userPrompt);

    /**
     * 带工具定义的聊天 — 返回文本或工具调用
     * 用于 ReAct Agent 等需要工具调用的场景
     *
     * @param messages        完整消息列表（含 system/user/assistant/tool 角色及 tool_calls）
     * @param toolDefinitions 工具定义列表（Provider 无关格式，含 name/description/parameters）
     * @return 响应（可能包含文本或工具调用）
     */
    Mono<LlmToolResponse> chatWithTools(List<ChatMessage> messages, List<Map<String, Object>> toolDefinitions);
}