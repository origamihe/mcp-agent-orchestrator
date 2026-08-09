package com.mcp.engine.reflection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.core.domain.memory.ReflectionLogEntity;
import com.mcp.core.domain.memory.ReflectionLogEntity.ReflectionOutcome;
import com.mcp.core.repository.ReflectionLogRepository;
import com.mcp.engine.retry.RetryManager;
import com.mcp.engine.retry.RetryTask;
import com.mcp.llm.client.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReflectionAgent {

    private final LlmClient llmClient;
    private final SkillLibraryService skillLibraryService;
    private final FailureLibraryService failureLibraryService;
    private final ReflectionLogRepository reflectionLogRepository;
    private final RetryManager retryManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int REFLECTION_MAX_RETRIES = 3;

    private static final String REFLECTION_PROMPT = """
        你是一个反思智能体。你的任务是从已完成的任务执行中提炼可复用的经验。

        【本轮任务信息】
        用户要求: %s
        Agent 执行: %s
        使用的工具: %s
        任务成功: %s
        失败原因: %s

        【反思要求】
        1. 分析为什么成功/失败
        2. 哪些步骤可以优化？
        3. 是否值得保存为经验？

        【如果值得保存，请输出 Skill】
        Skill 格式：
        {
            "type": "SKILL",
            "name": "Skill 名称（简洁）",
            "description": "Skill 描述",
            "triggers": ["触发词1", "触发词2"],
            "steps": [
                {"tool": "工具名", "params": {"key": "value"}, "priority": 1},
                {"tool": "工具名", "params": {"key": "value"}, "priority": 2, "isFallback": true}
            ],
            "fallbackSteps": [],
            "shouldSave": true
        }

        【如果失败，请输出 Failure】
        Failure 格式：
        {
            "type": "FAILURE",
            "taskPattern": "任务模式",
            "errorSignature": "错误签名",
            "rootCause": "根因分析",
            "correctApproach": "正确做法",
            "shouldSave": true
        }

        【如果不值得保存】
        {
            "type": "NONE",
            "shouldSave": false,
            "reason": "原因"
        }

        请输出 JSON（不要包含 markdown 标记）：
        """;

    @Async
    public void reflect(TaskEvaluator.TaskEvaluation evaluation,
                        String userRequest, String agentExecution,
                        List<String> toolsUsed, String sessionId, String userId) {
        if (!evaluation.isWorthLearning()) {
            log.info("[ReflectionAgent] 不值得学习，跳过");
            saveReflectionLog(evaluation, userRequest, agentExecution, toolsUsed,
                    sessionId, userId, null, ReflectionOutcome.DISCARDED, null, null);
            return;
        }

        reflectAsync(evaluation, userRequest, agentExecution, toolsUsed, sessionId, userId)
                .doOnError(error -> {
                    if (RetryManager.isRetryable(error)) {
                        log.warn("[ReflectionAgent] LLM 调用失败，提交重试: session={}, error={}",
                                sessionId, error.getMessage());
                        retryManager.submit(RetryTask.builder()
                                .sessionId(sessionId)
                                .userId(userId)
                                .taskType(RetryTask.TaskType.REFLECTION)
                                .action(() -> reflectAsync(evaluation, userRequest, agentExecution,
                                        toolsUsed, sessionId, userId))
                                .maxRetries(REFLECTION_MAX_RETRIES)
                                .build());
                    } else {
                        log.error("[ReflectionAgent] 不可重试的错误: session={}, error={}",
                                sessionId, error.getMessage());
                        saveReflectionLog(evaluation, userRequest, agentExecution, toolsUsed,
                                sessionId, userId, null, ReflectionOutcome.DISCARDED, null, null);
                    }
                })
                .subscribe();
    }

    Mono<Void> reflectAsync(TaskEvaluator.TaskEvaluation evaluation,
                            String userRequest, String agentExecution,
                            List<String> toolsUsed, String sessionId, String userId) {
        String toolsUsedText = (toolsUsed != null && !toolsUsed.isEmpty())
                ? String.join(", ", toolsUsed)
                : "无";
        String prompt = REFLECTION_PROMPT.formatted(
                truncate(userRequest, 2000),
                truncate(agentExecution, 3000),
                toolsUsedText,
                evaluation.isSuccess() ? "是" : "否",
                evaluation.failureReason() != null ? evaluation.failureReason() : "无");

        return llmClient.generate(prompt)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(response -> {
                    processReflectionResult(response, evaluation, userRequest,
                            agentExecution, toolsUsed, sessionId, userId);
                    return Mono.empty();
                });
    }

    private void processReflectionResult(String response,
                                         TaskEvaluator.TaskEvaluation evaluation,
                                         String userRequest, String agentExecution,
                                         List<String> toolsUsed, String sessionId, String userId) {
        try {
            String json = extractJson(response);
            Map<String, Object> result = objectMapper.readValue(
                    json, new TypeReference<Map<String, Object>>() {});

            String type = (String) result.getOrDefault("type", "NONE");
            boolean shouldSave = Boolean.TRUE.equals(result.get("shouldSave"));

            if (!shouldSave) {
                String reason = (String) result.getOrDefault("reason", "无价值");
                log.info("[ReflectionAgent] 反思结果: 不值得保存, reason={}", reason);
                saveReflectionLog(evaluation, userRequest, agentExecution, toolsUsed,
                        sessionId, userId, response, ReflectionOutcome.DISCARDED, null, null);
                return;
            }

            switch (type) {
                case "SKILL" -> {
                    Long skillId = processSkillResult(result);
                    ReflectionOutcome outcome = (skillId != null)
                            ? ReflectionOutcome.SKILL_GENERATED
                            : ReflectionOutcome.DISCARDED;
                    saveReflectionLog(evaluation, userRequest, agentExecution, toolsUsed,
                            sessionId, userId, response, outcome, skillId, null);
                }
                case "FAILURE" -> {
                    Long failureId = processFailureResult(result);
                    saveReflectionLog(evaluation, userRequest, agentExecution, toolsUsed,
                            sessionId, userId, response,
                            ReflectionOutcome.FAILURE_RECORDED, null, failureId);
                }
                default -> {
                    log.info("[ReflectionAgent] 未知 reflection type: {}", type);
                    saveReflectionLog(evaluation, userRequest, agentExecution, toolsUsed,
                            sessionId, userId, response,
                            ReflectionOutcome.DISCARDED, null, null);
                }
            }
        } catch (Exception e) {
            log.error("[ReflectionAgent] 处理反思结果失败: {}", e.getMessage());
            saveReflectionLog(evaluation, userRequest, agentExecution, toolsUsed,
                    sessionId, userId, null, ReflectionOutcome.DISCARDED, null, null);
        }
    }

    private Long processSkillResult(Map<String, Object> result) {
        String name = (String) result.getOrDefault("name", "未命名技能");
        String description = (String) result.getOrDefault("description", "");
        String triggers = toJsonString(result.get("triggers"));
        String steps = toJsonString(result.get("steps"));
        String fallbackSteps = toJsonString(result.get("fallbackSteps"));

        var skill = skillLibraryService.createOrUpdate(
                name, description, triggers, steps, fallbackSteps);
        if (skill == null) {
            log.info("[ReflectionAgent] Skill 因与已有 Skill 高度相似而跳过: {}", name);
            return null;
        }
        log.info("[ReflectionAgent] Skill 已保存: {} (v{})", skill.getName(), skill.getVersion());
        return skill.getId();
    }

    private Long processFailureResult(Map<String, Object> result) {
        String taskPattern = (String) result.getOrDefault("taskPattern", "未知任务");
        String errorSignature = (String) result.getOrDefault("errorSignature", "未知错误");
        String rootCause = (String) result.getOrDefault("rootCause", "未知原因");
        String correctApproach = (String) result.getOrDefault("correctApproach", "无建议");
        String contextSnapshot = toJsonString(result.get("context"));

        var failure = failureLibraryService.createOrUpdate(
                taskPattern, errorSignature, rootCause, correctApproach, contextSnapshot);
        log.info("[ReflectionAgent] Failure 已记录: taskPattern='{}'", failure.getTaskPattern());
        return failure.getId();
    }

    private void saveReflectionLog(TaskEvaluator.TaskEvaluation evaluation,
                                   String userRequest, String agentExecution,
                                   List<String> toolsUsed, String sessionId, String userId,
                                   String reflectionText, ReflectionOutcome outcome,
                                   Long skillId, Long failureId) {
        try {
            ReflectionLogEntity logEntry = ReflectionLogEntity.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .userRequest(truncate(userRequest, 4000))
                    .agentExecution(truncate(agentExecution, 4000))
                    .toolsUsed(toolsUsed)
                    .taskSuccess(evaluation.isSuccess())
                    .failureReason(evaluation.failureReason())
                    .reflection(reflectionText)
                    .worthLearning(evaluation.isWorthLearning())
                    .generatedSkillId(skillId)
                    .generatedFailureId(failureId)
                    .outcome(outcome)
                    .build();
            reflectionLogRepository.save(logEntry);
            log.info("[ReflectionAgent] ReflectionLog 已保存: session={}, outcome={}",
                    sessionId, outcome);
        } catch (Exception e) {
            log.error("[ReflectionAgent] 保存 ReflectionLog 失败: {}", e.getMessage());
        }
    }

    private String toJsonString(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
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

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }
}