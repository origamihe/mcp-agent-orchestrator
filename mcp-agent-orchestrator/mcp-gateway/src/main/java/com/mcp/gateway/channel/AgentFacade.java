package com.mcp.gateway.channel;

import com.mcp.engine.orchestrator.AgentOrchestrator;
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
}