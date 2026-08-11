package com.mcp.engine.orchestrator;

import com.mcp.core.service.PromptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Orchestrator 专用 Prompt 模板服务。
 * 封装 PromptService，提供带默认值的模板渲染。
 * 如果 DB 中未配置模板，自动回退到硬编码默认值。
 */
@Service
@Slf4j
public class OrchestratorPromptService {

    private final PromptService promptService;

    public OrchestratorPromptService(PromptService promptService) {
        this.promptService = promptService;
    }

    public Mono<String> render(String templateName, Map<String, Object> variables) {
        return promptService.renderPrompt(templateName, variables)
                .onErrorResume(e -> {
                    log.debug("[OrchestratorPrompt] Template '{}' not found in DB, using default fallback", templateName);
                    String fallback = getDefaultFallback(templateName);
                    return Mono.just(renderFallback(fallback, variables));
                });
    }

    private String renderFallback(String template, Map<String, Object> variables) {
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            result = result.replace(placeholder, String.valueOf(entry.getValue()));
        }
        return result;
    }

    private String getDefaultFallback(String templateName) {
        return switch (templateName) {
            case "orchestrator_final_answer" -> """
                    你是一个专业的 AI 助手。以下是工具执行的结果和用户请求，请根据这些信息给出专业、清晰的回答。
                    
                    {{observations_context}}
                    
                    ## 用户请求
                    {{user_request}}
                    
                    请基于以上工具执行结果，用中文给出专业、清晰的回答。如果某些步骤失败，请说明原因并给出替代建议。
                    """;

            case "orchestrator_recall_history_user_only" -> """
                    用户要求回顾自己说过的话，以下仅列出用户发送的消息：
                    
                    {{history_context}}
                    
                    用户的具体请求：{{user_request}}
                    
                    要求：
                    1. 只列出用户发送过的消息，按编号逐条回复
                    2. 必须基于真实聊天记录，不要编造内容
                    3. 如果用户要求"逐条列出"或"全部列举"，请完整列出所有用户消息
                    4. 如果聊天记录为空，请如实告知用户
                    """;

            case "orchestrator_recall_history_conversation" -> """
                    用户要求回顾完整聊天对话，以下是该会话的真实聊天历史：
                    
                    {{history_context}}
                    
                    用户的具体请求：{{user_request}}
                    
                    要求：
                    1. 必须基于真实聊天记录回答，不要编造内容
                    2. 如果用户要求"逐条列出"，请按编号逐条回复（包含用户和助手双方）
                    3. 如果用户要求"总结"，请按主题或时间线归纳
                    4. 如果聊天记录为空，请如实告知用户
                    """;

            case "orchestrator_recall_history_both" -> """
                    用户要求同时回顾自己说过的话和完整聊天记录，请先列出用户消息，再列出完整对话：
                    
                    {{history_context}}
                    
                    用户的具体请求：{{user_request}}
                    
                    要求：
                    1. 先列出用户消息部分（仅用户发送的消息）
                    2. 再列出完整对话部分（包含用户和助手双方）
                    3. 必须基于真实聊天记录，不要编造内容
                    4. 如果聊天记录为空，请如实告知用户
                    """;

            case "orchestrator_user_prompt_prefix" -> "用户消息：{{user_message}}";

            default -> {
                log.warn("[OrchestratorPrompt] Unknown template: '{}', returning empty string", templateName);
                yield "";
            }
        };
    }
}