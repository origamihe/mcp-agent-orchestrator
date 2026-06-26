package com.mcp.engine.planner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.llm.client.LlmClient;
import com.mcp.tools.model.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultPlanner implements Planner {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int DEFAULT_MAX_STEPS = 8;

    @Override
    public Mono<EditPlan> plan(String userRequest, PlanContext context) {
        if (context == null) {
            context = PlanContext.builder()
                    .availableTools(List.of())
                    .maxSteps(DEFAULT_MAX_STEPS)
                    .build();
        }

        String planningPrompt = buildPlanningPrompt(userRequest, context);
        String userPrompt = "请为以下用户请求生成执行计划：\n" + userRequest;

        return llmClient.generateWithSystemPrompt(planningPrompt, userPrompt)
                .map(this::parsePlanResponse)
                .doOnSuccess(plan -> log.info("[Planner] Plan generated: type={}, steps={}, complexity={}",
                        plan.getPlanType(), plan.getSteps().size(), plan.getEstimatedComplexity()))
                .doOnError(e -> log.error("[Planner] Plan generation failed", e))
                .onErrorReturn(buildFallbackPlan(userRequest));
    }

    private String buildPlanningPrompt(String userRequest, PlanContext context) {
        StringBuilder sb = new StringBuilder(2048);

        sb.append("你是一个任务规划器。根据用户请求和可用工具，生成结构化的执行计划。\n\n");

        sb.append("## 可用工具\n");
        if (context.getAvailableTools() != null && !context.getAvailableTools().isEmpty()) {
            for (ToolDefinition tool : context.getAvailableTools()) {
                sb.append("- **").append(tool.getName()).append("**: ")
                        .append(tool.getDescription()).append("\n");
            }
        } else {
            sb.append("（无可用工具，仅能进行对话）\n");
        }

        sb.append("\n## 规划规则\n");
        sb.append("1. 如果用户的问题不需要任何工具，planType 设为 CHAT，steps 为空。\n");
        sb.append("2. 如果只需要读取/搜索信息，planType 设为 READ_ONLY。\n");
        sb.append("3. 如果需要修改代码，planType 设为 CODE_EDIT，先规划读取步骤再规划修改步骤。\n");
        sb.append("4. 如果需要生成文件，planType 设为 GENERATE。\n");
        sb.append("5. 复杂多步骤任务，planType 设为 MULTI_STEP。\n");
        sb.append("6. 步骤数不超过 ").append(context.getMaxSteps()).append(" 个。\n");
        sb.append("7. 修改代码前必须先读取相关文件。\n");
        sb.append("8. 每个步骤标注 reason（为什么需要这一步）。\n");

        sb.append("\n## 输出格式（严格 JSON）\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"intent\": \"用户意图的一句话总结\",\n");
        sb.append("  \"planType\": \"CHAT|READ_ONLY|CODE_EDIT|GENERATE|MULTI_STEP\",\n");
        sb.append("  \"reasoning\": \"推理过程\",\n");
        sb.append("  \"steps\": [\n");
        sb.append("    {\n");
        sb.append("      \"type\": \"READ|SEARCH|ANALYZE|MODIFY|VALIDATE|OBSERVE\",\n");
        sb.append("      \"toolName\": \"工具名称\",\n");
        sb.append("      \"arguments\": {\"key\": \"value\"},\n");
        sb.append("      \"reason\": \"为什么需要这一步\",\n");
        sb.append("      \"dependsOn\": []\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"estimatedComplexity\": 3,\n");
        sb.append("  \"risks\": [\"潜在风险\"],\n");
        sb.append("  \"testStrategy\": \"测试策略\"\n");
        sb.append("}\n");
        sb.append("```\n");

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private EditPlan parsePlanResponse(String llmResponse) {
        try {
            String json = extractJson(llmResponse);
            Map<String, Object> raw = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            String planTypeStr = (String) raw.getOrDefault("planType", "CHAT");
            EditPlan.PlanType planType;
            try {
                planType = EditPlan.PlanType.valueOf(planTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                planType = EditPlan.PlanType.CHAT;
            }

            List<Map<String, Object>> rawSteps = (List<Map<String, Object>>) raw.getOrDefault("steps", List.of());
            List<PlanStep> steps = rawSteps.stream()
                    .map(this::parseStep)
                    .collect(Collectors.toList());

            int complexity = raw.get("estimatedComplexity") instanceof Number n
                    ? n.intValue() : 1;

            List<String> risks = (List<String>) raw.getOrDefault("risks", List.of());

            return EditPlan.builder()
                    .intent((String) raw.getOrDefault("intent", ""))
                    .planType(planType)
                    .reasoning((String) raw.getOrDefault("reasoning", ""))
                    .steps(steps)
                    .estimatedComplexity(Math.max(1, Math.min(5, complexity)))
                    .risks(risks)
                    .testStrategy((String) raw.getOrDefault("testStrategy", ""))
                    .build();
        } catch (Exception e) {
            log.warn("[Planner] Failed to parse plan JSON, using fallback. Error: {}", e.getMessage());
            throw new RuntimeException("Plan parsing failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private PlanStep parseStep(Map<String, Object> raw) {
        String typeStr = (String) raw.getOrDefault("type", "READ");
        PlanStep.StepType stepType;
        try {
            stepType = PlanStep.StepType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            stepType = PlanStep.StepType.READ;
        }

        Map<String, Object> args = (Map<String, Object>) raw.getOrDefault("arguments", Map.of());
        List<String> dependsOn = (List<String>) raw.getOrDefault("dependsOn", List.of());

        return PlanStep.builder()
                .type(stepType)
                .toolName((String) raw.get("toolName"))
                .arguments(args)
                .reason((String) raw.getOrDefault("reason", ""))
                .dependsOn(dependsOn)
                .build();
    }

    private String extractJson(String response) {
        if (response == null || response.isBlank()) {
            return "{}";
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    private EditPlan buildFallbackPlan(String userRequest) {
        log.warn("[Planner] Using fallback plan for request: {}", userRequest);
        return EditPlan.builder()
                .intent(userRequest)
                .planType(EditPlan.PlanType.CHAT)
                .reasoning("Planner failed, falling back to direct chat")
                .steps(List.of())
                .estimatedComplexity(1)
                .risks(List.of())
                .testStrategy("")
                .build();
    }
}