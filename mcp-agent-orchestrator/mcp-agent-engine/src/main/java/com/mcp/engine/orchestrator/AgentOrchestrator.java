package com.mcp.engine.orchestrator;

import com.mcp.common.context.RequestContext;
import com.mcp.common.identity.MemoryIdentity;
import com.mcp.engine.agent.Agent;
import com.mcp.common.channel.RecallMode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Agent 调度中台核心
 */
public interface AgentOrchestrator {

    Mono<String> processRequestWithModel(String request, String sessionId, String modelConfigId);

    Mono<String> processRequestWithSystemPrompt(String request, String sessionId, String systemPrompt, String modelConfigId);

    /**
     * 带完整身份信息的请求处理（推荐使用）。
     * 调用方已持有 senderId/groupId/platform 时，直接传入 MemoryIdentity，
     * 避免 sessionId → MemoryIdentity 的二次解析。
     */
    Mono<String> processRequestWithIdentity(String request, MemoryIdentity identity, String systemPrompt, String modelConfigId);

    /**
     * 统一请求入口（推荐使用）。
     * 
     * 接受 RequestContext 统一上下文对象，包含身份、用户资料、群组上下文、
     * 会话状态、工作空间等所有元数据。未来新增上下文字段无需修改此接口。
     * 
     * @param ctx 统一请求上下文
     * @return LLM 响应
     */
    Mono<String> processRequest(RequestContext ctx);

    Mono<String> processRequestWithHistory(String request, String sessionId, String systemPrompt, RecallMode recallMode);

    void registerAgent(Agent agent);

    void registerDefaultTools();

    /**
     * 流式处理请求 — 逐 token 返回 LLM 响应。
     *
     * @param request       用户消息
     * @param sessionId     会话 ID
     * @param systemPrompt  系统提示（可选）
     * @param modelConfigId 模型配置 ID（可选）
     * @return 流式 token 序列
     */
    Flux<String> processRequestStream(String request, String sessionId, String systemPrompt, String modelConfigId);
}