package com.mcp.gateway.ws;

import com.mcp.engine.orchestrator.AgentOrchestrator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Component
public class McpWebSocketHandler implements WebSocketHandler {

    private final AgentOrchestrator orchestrator;

    public McpWebSocketHandler(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        System.out.println("WebSocket success connect: " + session.getId());

        return session.receive()
                .map(msg -> msg.getPayloadAsText())
                .doOnNext(msg -> System.out.println("receive: " + msg))
                .flatMap(userMessage ->
                        orchestrator.processRequest(userMessage, session.getId())
                                .flatMap(response -> {
                                    System.out.println("prepare send to front: " + response);
                                    return session.send(Mono.just(session.textMessage(response)))
                                            .doOnSuccess(v -> System.out.println(" success send msg"));
                                })
                )
                .onErrorContinue((err, obj) ->
                        System.err.println("process error: " + err.getMessage())
                )
                .then();
    }
}