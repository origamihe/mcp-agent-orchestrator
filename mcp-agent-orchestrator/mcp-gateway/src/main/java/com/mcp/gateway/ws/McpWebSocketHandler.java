package com.mcp.gateway.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.core.service.PromptService;
import com.mcp.engine.orchestrator.AgentOrchestrator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Component
public class McpWebSocketHandler implements WebSocketHandler {

    private final AgentOrchestrator orchestrator;
    private final PromptService promptService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpWebSocketHandler(AgentOrchestrator orchestrator, PromptService promptService) {
        this.orchestrator = orchestrator;
        this.promptService = promptService;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        System.out.println("WebSocket success connect: " + session.getId());

        return session.receive()
                .map(msg -> msg.getPayloadAsText())
                .doOnNext(msg -> System.out.println("receive: " + msg))
                .flatMap(rawMessage -> {
                    ParsedMessage pm = parseMessage(rawMessage);
                    final String userMessage = pm.userMessage;
                    final String modelConfigId = pm.modelConfigId;
                    final String systemPromptName = pm.systemPromptName;

                    if (systemPromptName != null && !systemPromptName.isEmpty()) {
                        final String mid = modelConfigId;
                        return promptService.getPrompt(systemPromptName)
                                .flatMap(prompt -> orchestrator.processRequestWithSystemPrompt(
                                        userMessage, session.getId(), prompt.getTemplateText(), mid))
                                .onErrorResume(err -> {
                                    System.err.println("Prompt '" + systemPromptName + "' not found, using default: " + err.getMessage());
                                    if (mid != null && !mid.isEmpty()) {
                                        return orchestrator.processRequestWithModel(userMessage, session.getId(), mid);
                                    }
                                    return orchestrator.processRequest(userMessage, session.getId());
                                })
                                .flatMap(response -> {
                                    System.out.println("prepare send to front: " + response);
                                    return session.send(Mono.just(session.textMessage(response)))
                                            .doOnSuccess(v -> System.out.println(" success send msg"));
                                });
                    } else if (modelConfigId != null && !modelConfigId.isEmpty()) {
                        return orchestrator.processRequestWithModel(userMessage, session.getId(), modelConfigId)
                                .flatMap(response -> {
                                    System.out.println("prepare send to front: " + response);
                                    return session.send(Mono.just(session.textMessage(response)))
                                            .doOnSuccess(v -> System.out.println(" success send msg"));
                                });
                    } else {
                        return orchestrator.processRequest(userMessage, session.getId())
                                .flatMap(response -> {
                                    System.out.println("prepare send to front: " + response);
                                    return session.send(Mono.just(session.textMessage(response)))
                                            .doOnSuccess(v -> System.out.println(" success send msg"));
                                });
                    }
                })
                .onErrorContinue((err, obj) ->
                        System.err.println("process error: " + err.getMessage())
                )
                .then();
    }

    private record ParsedMessage(String userMessage, String modelConfigId, String systemPromptName) {}

    private ParsedMessage parseMessage(String rawMessage) {
        try {
            JsonNode json = objectMapper.readTree(rawMessage);
            String msg = json.has("message") ? json.get("message").asText() : rawMessage;
            String cfgId = (json.has("modelConfigId") && !json.get("modelConfigId").isNull())
                    ? json.get("modelConfigId").asText() : null;
            String promptName = (json.has("systemPromptName") && !json.get("systemPromptName").isNull())
                    ? json.get("systemPromptName").asText() : null;
            return new ParsedMessage(msg, cfgId, promptName);
        } catch (Exception e) {
            return new ParsedMessage(rawMessage, null, null);
        }
    }
}