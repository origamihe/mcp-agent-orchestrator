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
        if (isSimpleChat(userRequest)) {
            log.debug("[TaskEvaluator] 简单闲聊，跳过评估");
            return Mono.just(TaskEvaluation.skip());
        }

        String prompt = EVALUATION_PROMPT.formatted(
                truncate(userRequest, 2000),
                truncate(agentExecution, 3000),
                toolsUsed != null ? toolsUsed : "无");

        return llmClient.generate(prompt)
                .map(this::parseEvaluation)
                .doOnNext(e -> log.info("[TaskEvaluator] 评估结果: score={}, success={}, worthLearning={}, type={}",
                        e.totalScore, e.isSuccess, e.isWorthLearning, e.learningType))
                .onErrorReturn(TaskEvaluation.skip());
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
        return lower.length() < 10
                || lower.matches("^(你好|hi|hello|谢谢|再见|好的|嗯|哦|哈哈).*")
                || (!lower.contains(" ") && lower.length() < 8);
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
            return new TaskEvaluation(0, 0, 0, 0, 0, true, false, "NONE", "", "");
        }

        public boolean shouldGenerateSkill() {
            return "SKILL".equals(learningType) || "BOTH".equals(learningType);
        }

        public boolean shouldRecordFailure() {
            return "FAILURE".equals(learningType) || "BOTH".equals(learningType);
        }
    }
}