package com.mcp.engine.trace;

import java.util.List;
import java.util.Map;

/**
 * 执行契约 — 验证 Agent 执行管线的每一步是否满足预期。
 *
 * 每个契约定义了：
 * 1. 触发条件（when）
 * 2. 预期结果（then）
 * 3. 验证逻辑（verify）
 *
 * 与 SessionTrace 配合使用：
 * - SessionTrace 记录事件流
 * - ExecutionContract 验证事件流中是否存在预期的执行路径
 *
 * 典型契约示例：
 * <pre>
 * GENERATE_DOCX
 *   → requires SEARCH
 *   → must_have SearchExecutionTrace
 *
 * SEARCH intent
 *   → Agent must be SearchAgent
 *   → toolResults must be > 0
 *
 * ContextRequirement.SEARCH
 *   → PromptContext.searchResults must not be null
 * </pre>
 */
@FunctionalInterface
public interface ExecutionContract {

    ContractResult verify(List<SessionEvent> events);

    record ContractResult(boolean passed, String contractName, String detail) {
        public static ContractResult pass(String name) {
            return new ContractResult(true, name, "");
        }

        public static ContractResult fail(String name, String detail) {
            return new ContractResult(false, name, detail);
        }
    }

    static ExecutionContract searchAgentMustExecuteTools() {
        return events -> {
            boolean hasSearchAgent = events.stream()
                    .anyMatch(e -> e.eventType() == SessionEventType.AGENT_SELECTION
                            && "SearchAgent".equals(e.payload().get("agentName")));

            boolean hasToolCall = events.stream()
                    .anyMatch(e -> e.eventType() == SessionEventType.TOOL_CALL);

            if (hasSearchAgent && !hasToolCall) {
                return ContractResult.fail("SearchAgentMustExecuteTools",
                        "SearchAgent was selected but no TOOL_CALL event found");
            }
            return ContractResult.pass("SearchAgentMustExecuteTools");
        };
    }

    static ExecutionContract searchAgentMustHaveToolResults() {
        return events -> {
            boolean hasSearchAgent = events.stream()
                    .anyMatch(e -> e.eventType() == SessionEventType.AGENT_SELECTION
                            && "SearchAgent".equals(e.payload().get("agentName")));

            boolean hasToolResult = events.stream()
                    .anyMatch(e -> e.eventType() == SessionEventType.TOOL_RESULT);

            if (hasSearchAgent && !hasToolResult) {
                return ContractResult.fail("SearchAgentMustHaveToolResults",
                        "SearchAgent was selected but no TOOL_RESULT event found");
            }
            return ContractResult.pass("SearchAgentMustHaveToolResults");
        };
    }

    static ExecutionContract docxGenerationMustRouteToSearch() {
        return events -> {
            boolean hasDocxTask = events.stream()
                    .anyMatch(e -> e.eventType() == SessionEventType.USER_MESSAGE
                            && e.payload().toString().contains("DOCX_GENERATION"));

            boolean hasSearchAgent = events.stream()
                    .anyMatch(e -> e.eventType() == SessionEventType.AGENT_SELECTION
                            && "SearchAgent".equals(e.payload().get("agentName")));

            if (hasDocxTask && !hasSearchAgent) {
                return ContractResult.fail("DocxGenerationMustRouteToSearch",
                        "DOCX_GENERATION task detected but SearchAgent was not selected");
            }
            return ContractResult.pass("DocxGenerationMustRouteToSearch");
        };
    }

    static ExecutionContract contextClassificationMustExist() {
        return events -> {
            boolean hasClassification = events.stream()
                    .anyMatch(e -> e.eventType() == SessionEventType.CONTEXT_CLASSIFICATION);

            if (!hasClassification) {
                return ContractResult.fail("ContextClassificationMustExist",
                        "No CONTEXT_CLASSIFICATION event found in trace");
            }
            return ContractResult.pass("ContextClassificationMustExist");
        };
    }

    static ExecutionContract toolCallMustHaveResult() {
        return events -> {
            List<SessionEvent> toolCalls = events.stream()
                    .filter(e -> e.eventType() == SessionEventType.TOOL_CALL)
                    .toList();
            List<SessionEvent> toolResults = events.stream()
                    .filter(e -> e.eventType() == SessionEventType.TOOL_RESULT)
                    .toList();

            for (SessionEvent call : toolCalls) {
                String toolName = (String) call.payload().get("toolName");
                boolean hasResult = toolResults.stream()
                        .anyMatch(r -> toolName.equals(r.payload().get("toolName")));
                if (!hasResult) {
                    return ContractResult.fail("ToolCallMustHaveResult",
                            "TOOL_CALL for '" + toolName + "' has no matching TOOL_RESULT");
                }
            }
            return ContractResult.pass("ToolCallMustHaveResult");
        };
    }

    static ExecutionContract systemPromptMustExist() {
        return events -> {
            boolean hasSystemPrompt = events.stream()
                    .anyMatch(e -> e.eventType() == SessionEventType.SYSTEM_PROMPT);

            if (!hasSystemPrompt) {
                return ContractResult.fail("SystemPromptMustExist",
                        "No SYSTEM_PROMPT event found in trace");
            }
            return ContractResult.pass("SystemPromptMustExist");
        };
    }
}