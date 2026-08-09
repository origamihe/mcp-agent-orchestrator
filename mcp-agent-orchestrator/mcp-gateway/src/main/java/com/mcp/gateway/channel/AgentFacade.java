package com.mcp.gateway.channel;

import com.mcp.common.context.RequestContext;
import com.mcp.common.identity.MemoryIdentity;
import com.mcp.engine.orchestrator.AgentOrchestrator;
import com.mcp.common.channel.RecallMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentFacade {

    private final AgentOrchestrator agentOrchestrator;

    public Mono<String> call(String userMessage, String sessionId, String systemPrompt) {
        return agentOrchestrator.processRequestWithSystemPrompt(
                        userMessage,
                        sessionId,
                        systemPrompt,
                        null
                )
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 带完整身份信息的调用（推荐使用）。
     * 调用方已持有 senderId/groupId/platform 时，直接传入 MemoryIdentity，
     * 避免 sessionId 字符串解析的二次开销。
     */
    public Mono<String> call(String userMessage, MemoryIdentity identity, String systemPrompt) {
        return agentOrchestrator.processRequestWithIdentity(
                        userMessage,
                        identity,
                        systemPrompt,
                        null
                )
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 统一请求入口（推荐使用）。
     * 
     * 接受 RequestContext 统一上下文对象，包含身份、用户资料、群组上下文、
     * 会话状态、工作空间等所有元数据。未来新增字段无需修改此方法签名。
     */
    public Mono<String> call(RequestContext ctx) {
        return agentOrchestrator.processRequest(ctx)
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<String> callWithHistory(String userMessage, String sessionId, String systemPrompt,
                                         RecallMode recallMode) {
        return agentOrchestrator.processRequestWithHistory(
                        userMessage,
                        sessionId,
                        systemPrompt,
                        recallMode
                )
                .subscribeOn(Schedulers.boundedElastic());
    }
}