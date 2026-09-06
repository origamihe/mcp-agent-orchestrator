package com.mcp.engine.agent;

import com.mcp.common.channel.SearchRequirement;
import com.mcp.engine.execution.ExecutionPlan;
import com.mcp.engine.execution.ExecutionState;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 统一 LLM 请求对象 — 所有 Agent 的唯一入参。
 *
 * 设计原则：
 * 1. 这是 Agent Runtime 层的唯一数据契约，替代原先的 String task + AgentContext 分散传参
 * 2. 所有 ContextProvider（Persona、Memory、Workspace、Skill、Reflection）的输出
 *    最终汇入此对象，由 PromptComposer 统一渲染为 layered system prompt
 * 3. 未来接入 DeepSeek / Claude / OpenAI / Gemini 时，只需适配此对象，无需修改 Agent 接口
 */
@Data
@Builder
public class LLMRequest {

    /** 会话ID */
    private String sessionId;

    /** 用户ID */
    private String userId;

    /** 群ID */
    private String groupId;

    /** 最终渲染好的分层 System Prompt（由 PromptComposer 统一生成） */
    private String systemPrompt;

    /** 用户原始消息 */
    private String userMessage;

    /** 温度参数（CONTRACTUAL: 当前未接入，由 LLM Config 直接控制） */
    @Builder.Default
    private Double temperature = 0.7;

    /** 可用工具列表（CONTRACTUAL: 当前未接入，工具由 Agent 内部 buildToolDefinitions() 构建） */
    @Builder.Default
    private List<String> tools = List.of();

    /** 记忆上下文（CONTRACTUAL: 当前未接入，记忆由 PromptComposer 分层注入） */
    private String memoryContext;

    /** 工作空间上下文（CONTRACTUAL: 当前未接入，工作空间由 WorkingContext 管理） */
    private String workspaceContext;

    /** 模型配置ID（可选，用于多模型切换） */
    private String modelConfigId;

    /** 执行计划（P1 核心：Agent 通过此字段获取 ToolPolicy / MemoryPolicy / TimeoutPolicy） */
    private ExecutionPlan executionPlan;

    /** 执行状态（P1 核心：Agent 通过此字段更新执行生命周期状态） */
    private ExecutionState executionState;

    /** 搜索需求级别（P2 核心：代码层判定是否需要搜索，替代 Prompt 判定） */
    @Builder.Default
    private SearchRequirement searchRequirement = SearchRequirement.NONE;

    /** 扩展变量 */
    @Builder.Default
    private Map<String, Object> variables = Map.of();

    /**
     * 快速创建仅包含 systemPrompt + userMessage 的请求。
     */
    public static LLMRequest of(String systemPrompt, String userMessage) {
        return LLMRequest.builder()
                .systemPrompt(systemPrompt)
                .userMessage(userMessage)
                .build();
    }

    /**
     * 快速创建带 sessionId 的请求。
     */
    public static LLMRequest of(String sessionId, String systemPrompt, String userMessage) {
        return LLMRequest.builder()
                .sessionId(sessionId)
                .systemPrompt(systemPrompt)
                .userMessage(userMessage)
                .build();
    }
}