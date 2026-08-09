package com.mcp.engine.reflection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.llm.client.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskEvaluator {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String EVALUATION_PROMPT = """
        你是一个任务评估器。请根据以下客观指标判断本次任务执行的质量。

        【用户请求】
        %s

        【Agent 执行过程】
        %s

        【使用的工具】
        %s

        【评估维度（每项 0-25 分，总分 0-100）】
        1. 任务完成度: 用户请求是否被完全满足？
        2. 工具正确性: 使用的工具是否合适？是否有更好的工具选择？
        3. 执行效率: 是否有多余的步骤？是否有不必要的重试？
        4. 可复用性: 这次执行中是否有值得提炼为可复用模式的经验？

        【输出格式】
        严格输出 JSON：
        {
            "completionScore": 0-25,
            "toolCorrectnessScore": 0-25,
            "efficiencyScore": 0-25,
            "reusabilityScore": 0-25,
            "totalScore": 0-100,
            "isSuccess": true/false,
            "isWorthLearning": true/false,
            "learningType": "SKILL / FAILURE / BOTH / NONE",
            "summary": "评估摘要",
            "failureReason": "如果失败，简述原因"
        }

        【判断标准】
        - totalScore >= 70 且 isSuccess = true → 可以考虑提炼 Skill
        - totalScore < 50 或 isSuccess = false → 应该记录 Failure
        - totalScore 在 50-70 之间且无特殊价值 → 直接丢弃
        - 简单闲聊、问候 → totalScore 自动为 0，isWorthLearning = false
        """;

    public Mono<TaskEvaluation> evaluate(String userRequest, String agentExecution,
                                         String toolsUsed) {
        return evaluate(userRequest, agentExecution, toolsUsed, false, 0, false);
    }

    /**
     * 带 Pipeline 执行元数据的评估。
     *
     * @param pipelineHadToolCalls Pipeline 是否实际执行了工具调用
     * @param pipelineToolResultCount Pipeline 中工具调用返回的结果数
     * @param pipelineHadParseFailure Pipeline 中是否发生了解析失败
     */
    public Mono<TaskEvaluation> evaluate(String userRequest, String agentExecution,
                                         String toolsUsed,
                                         boolean pipelineHadToolCalls,
                                         int pipelineToolResultCount,
                                         boolean pipelineHadParseFailure) {
        if (isSimpleChat(userRequest)) {
            log.debug("[TaskEvaluator] 简单闲聊，跳过评估");
            return Mono.just(TaskEvaluation.skip());
        }

        String pipelineInfo = buildPipelineInfo(pipelineHadToolCalls, pipelineToolResultCount, pipelineHadParseFailure);

        String prompt = EVALUATION_PROMPT.formatted(
                truncate(userRequest, 2000),
                truncate(agentExecution + "\n\n" + pipelineInfo, 3000),
                toolsUsed != null ? toolsUsed : "无");

        return llmClient.generate(prompt)
                .map(response -> parseEvaluationWithPipelineMeta(response, pipelineHadToolCalls,
                        pipelineToolResultCount, pipelineHadParseFailure))
                .doOnNext(e -> log.info("[TaskEvaluator] 评估结果: score={}, success={}, worthLearning={}, type={}",
                        e.totalScore, e.isSuccess, e.isWorthLearning, e.learningType))
                .onErrorReturn(TaskEvaluation.skip());
    }

    /**
     * 构建 Pipeline 执行元数据信息，供 LLM 评估时参考。
     */
    private String buildPipelineInfo(boolean hadToolCalls, int toolResultCount, boolean hadParseFailure) {
        StringBuilder sb = new StringBuilder();
        sb.append("【Pipeline 执行元数据】\n");
        sb.append("- 工具调用: ").append(hadToolCalls ? "是" : "否").append("\n");
        sb.append("- 工具返回结果数: ").append(toolResultCount).append("\n");
        sb.append("- 解析失败: ").append(hadParseFailure ? "是" : "否").append("\n");

        if (hadToolCalls && toolResultCount == 0) {
            sb.append("⚠️ 警告: Pipeline 执行了工具调用但没有收到任何结果，这表示工具执行或结果传递链存在问题。\n");
        }
        if (hadParseFailure) {
            sb.append("⚠️ 警告: Pipeline 在解析工具结果时发生了失败，搜索结果可能丢失。\n");
        }
        return sb.toString();
    }

    /**
     * 解析 LLM 响应，并结合 Pipeline 元数据修正 isSuccess 判定。
     */
    private TaskEvaluation parseEvaluationWithPipelineMeta(String llmResponse,
                                                            boolean hadToolCalls,
                                                            int toolResultCount,
                                                            boolean hadParseFailure) {
        TaskEvaluation llmEval = parseEvaluation(llmResponse);

        // 如果 Pipeline 元数据表明执行失败，覆盖 LLM 的乐观判断
        boolean pipelineFailed = (hadToolCalls && toolResultCount == 0) || hadParseFailure;

        if (pipelineFailed && llmEval.isSuccess) {
            log.warn("[TaskEvaluator] LLM 判断为成功，但 Pipeline 元数据显示执行失败，覆盖 isSuccess=false");
            return new TaskEvaluation(
                    Math.min(llmEval.completionScore, 25),
                    Math.min(llmEval.toolCorrectnessScore, 10),
                    llmEval.efficiencyScore,
                    llmEval.reusabilityScore,
                    Math.min(llmEval.totalScore, 40),
                    false,
                    true,
                    "FAILURE",
                    llmEval.summary,
                    "Pipeline 执行失败: toolCalls=" + hadToolCalls
                            + ", toolResults=" + toolResultCount
                            + ", parseFailure=" + hadParseFailure
            );
        }

        return llmEval;
    }

    private TaskEvaluation parseEvaluation(String llmResponse) {
        try {
            String json = extractJson(llmResponse);
            var node = objectMapper.readTree(json);

            int completionScore = node.has("completionScore") ? node.get("completionScore").asInt(0) : 0;
            int toolCorrectnessScore = node.has("toolCorrectnessScore") ? node.get("toolCorrectnessScore").asInt(0) : 0;
            int efficiencyScore = node.has("efficiencyScore") ? node.get("efficiencyScore").asInt(0) : 0;
            int reusabilityScore = node.has("reusabilityScore") ? node.get("reusabilityScore").asInt(0) : 0;
            int totalScore = node.has("totalScore") ? node.get("totalScore").asInt(0)
                    : completionScore + toolCorrectnessScore + efficiencyScore + reusabilityScore;
            boolean isSuccess = node.has("isSuccess") && node.get("isSuccess").asBoolean();
            boolean isWorthLearning = node.has("isWorthLearning") && node.get("isWorthLearning").asBoolean();
            String learningType = node.has("learningType") ? node.get("learningType").asText("NONE") : "NONE";
            String summary = node.has("summary") ? node.get("summary").asText("") : "";
            String failureReason = node.has("failureReason") ? node.get("failureReason").asText("") : "";

            return new TaskEvaluation(completionScore, toolCorrectnessScore, efficiencyScore,
                    reusabilityScore, totalScore, isSuccess, isWorthLearning,
                    learningType, summary, failureReason);
        } catch (Exception e) {
            log.error("[TaskEvaluator] 解析 LLM 响应失败: {}", e.getMessage());
            return TaskEvaluation.skip();
        }
    }

    private String extractJson(String response) {
        String trimmed = response.trim();
        if (trimmed.startsWith("```json")) {
            int start = trimmed.indexOf("{");
            int end = trimmed.lastIndexOf("}");
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        int start = trimmed.indexOf("{");
        int end = trimmed.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return "{}";
    }

    private boolean isSimpleChat(String request) {
        if (request == null) return true;
        String lower = request.toLowerCase().trim();
        return lower.length() < 4
                || lower.matches("^(你好|hi|hello|谢谢|再见|好的|嗯|哦|哈哈).*");
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }

    public record TaskEvaluation(
            int completionScore,
            int toolCorrectnessScore,
            int efficiencyScore,
            int reusabilityScore,
            int totalScore,
            boolean isSuccess,
            boolean isWorthLearning,
            String learningType,
            String summary,
            String failureReason
    ) {
        public static TaskEvaluation skip() {
            return new TaskEvaluation(0, 0, 0, 0, 0, false, false, "NONE", "", "");
        }

        public boolean shouldGenerateSkill() {
            return "SKILL".equals(learningType) || "BOTH".equals(learningType);
        }

        public boolean shouldRecordFailure() {
            return "FAILURE".equals(learningType) || "BOTH".equals(learningType);
        }
    }
}