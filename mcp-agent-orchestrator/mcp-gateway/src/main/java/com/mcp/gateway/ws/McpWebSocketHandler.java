package com.mcp.gateway.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.core.service.PromptService;
import com.mcp.engine.orchestrator.AgentOrchestrator;
import com.mcp.tools.tool.FetchWebpageTool;
import com.mcp.tools.tool.MultiSearchTool;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class McpWebSocketHandler implements WebSocketHandler {

    private final AgentOrchestrator orchestrator;
    private final PromptService promptService;
    private final MultiSearchTool multiSearchTool;
    private final FetchWebpageTool fetchWebpageTool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpWebSocketHandler(AgentOrchestrator orchestrator, PromptService promptService,
                                MultiSearchTool multiSearchTool, FetchWebpageTool fetchWebpageTool) {
        this.orchestrator = orchestrator;
        this.promptService = promptService;
        this.multiSearchTool = multiSearchTool;
        this.fetchWebpageTool = fetchWebpageTool;
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
                    final String featureId = pm.featureId;

                    if ("web-search".equals(featureId)) {
                        String url = FetchWebpageTool.extractFirstUrl(userMessage);
                        if (url != null) {
                            return Mono.fromCallable(() -> fetchWebpageTool.fetchWebpage(url))
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .flatMap(webpageContent -> {
                                        String prompt = "请根据以下网页内容，直接回答用户的问题。" +
                                                "要求：不要展示思考过程，不要使用markdown代码块，直接给出答案。\n\n" +
                                                "用户问题：" + userMessage + "\n\n网页内容：\n" + webpageContent;
                                        return orchestrator.processRequestWithModel(prompt, session.getId(), modelConfigId);
                                    })
                                    .flatMap(response -> session.send(Mono.just(session.textMessage(response))));
                        }
                        return Mono.fromCallable(() -> multiSearchTool.multiSearch(userMessage))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(searchResult -> {
                                    String prompt = "你是一个专业的信息分析助手。用户正在进行联网搜索，请执行以下任务：\n\n" +
                                            "【用户问题】" + userMessage + "\n\n" +
                                            "【多源搜索结果】\n" + searchResult + "\n\n" +
                                            "【你的任务】\n" +
                                            "1. 仔细阅读以上来自多个信息源（Google/DDG/Wikipedia等）的搜索结果\n" +
                                            "2. 对不同来源的信息进行横向对比：标注一致点和分歧点\n" +
                                            "3. 去重后整合信息，给出权威、全面的回答\n" +
                                            "4. 如果某条信息有明确来源，请标注来源\n\n" +
                                            "【重要格式要求】\n" +
                                            "- 直接输出最终分析结果，不要展示思考过程\n" +
                                            "- 不要使用```markdown```等代码块标记\n" +
                                            "- 使用自然的段落和换行，让内容易读\n" +
                                            "- 如有表格，用文字列表或分行描述代替";
                                    return orchestrator.processRequestWithModel(prompt, session.getId(), modelConfigId);
                                })
                                .flatMap(response -> session.send(Mono.just(session.textMessage(response))));
                    }

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

    private record ParsedMessage(String userMessage, String modelConfigId, String systemPromptName, String featureId) {}

    private ParsedMessage parseMessage(String rawMessage) {
        try {
            JsonNode json = objectMapper.readTree(rawMessage);
            String msg = json.has("message") ? json.get("message").asText() : rawMessage;
            String cfgId = (json.has("modelConfigId") && !json.get("modelConfigId").isNull())
                    ? json.get("modelConfigId").asText() : null;
            String promptName = (json.has("systemPromptName") && !json.get("systemPromptName").isNull())
                    ? json.get("systemPromptName").asText() : null;
            String featureId = (json.has("featureId") && !json.get("featureId").isNull())
                    ? json.get("featureId").asText() : null;
            return new ParsedMessage(msg, cfgId, promptName, featureId);
        } catch (Exception e) {
            return new ParsedMessage(rawMessage, null, null, null);
        }
    }
}