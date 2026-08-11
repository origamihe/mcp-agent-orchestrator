package com.mcp.tools.pipeline;

import com.mcp.tools.executor.ToolExecutor;
import com.mcp.tools.model.ToolExecutionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工具管道执行器 — 按顺序执行管道步骤，支持步骤间输出引用。
 * <p>
 * 引用语法：${stepId} 或 ${stepId.fieldName}
 * <ul>
 *   <li>${stepId} — 引用指定步骤的完整输出</li>
 *   <li>${stepId.data} — 引用指定步骤输出中 data 字段的值</li>
 *   <li>${stepId.message} — 引用指定步骤输出中 message 字段的值</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolPipelineExecutor {

    private final ToolExecutor toolExecutor;

    private static final Pattern REF_PATTERN = Pattern.compile("\\$\\{([^.}]+)(?:\\.([^}]+))?}");

    private static final Object TOOL_FAILED_SENTINEL = new Object();

    /**
     * 执行管道，返回最终结果。
     */
    public Mono<ToolPipelineResult> execute(ToolPipeline pipeline) {
        long startTime = System.currentTimeMillis();
        Map<String, ToolPipelineResult.StepResult> stepResults = new LinkedHashMap<>();
        Map<String, Object> stepOutputs = new LinkedHashMap<>();

        return executeSteps(pipeline, pipeline.getSteps(), stepResults, stepOutputs, 0)
                .map(lastOutput -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    return ToolPipelineResult.builder()
                            .pipelineId(pipeline.getPipelineId())
                            .success(true)
                            .stepResults(stepResults)
                            .finalOutput(lastOutput)
                            .elapsedMs(elapsed)
                            .build();
                })
                .onErrorResume(ex -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    return Mono.just(ToolPipelineResult.builder()
                            .pipelineId(pipeline.getPipelineId())
                            .success(false)
                            .stepResults(stepResults)
                            .error(ex.getMessage())
                            .elapsedMs(elapsed)
                            .build());
                })
                .timeout(Duration.ofSeconds(pipeline.getTimeoutSeconds()));
    }

    private Mono<Object> executeSteps(ToolPipeline pipeline,
                                       java.util.List<ToolPipelineStep> steps,
                                       Map<String, ToolPipelineResult.StepResult> stepResults,
                                       Map<String, Object> stepOutputs,
                                       int index) {
        if (index >= steps.size()) {
            return Mono.just(stepResults.isEmpty() ? "empty pipeline" : getLastOutput(stepOutputs));
        }

        ToolPipelineStep step = steps.get(index);
        long stepStart = System.currentTimeMillis();

        Map<String, Object> resolvedArgs = resolveArguments(step.getArguments(), stepOutputs);

        ToolExecutionRequest execRequest = new ToolExecutionRequest();
        execRequest.setToolName(step.getToolName());
        execRequest.setArguments(resolvedArgs);
        execRequest.setRequestId(UUID.randomUUID().toString());

        log.info("[Pipeline:{}] Step {}/{}: {} | args={}",
                pipeline.getPipelineId(), index + 1, steps.size(), step.getToolName(), resolvedArgs.keySet());

        return toolExecutor.execute(execRequest)
                .timeout(Duration.ofSeconds(step.getTimeoutSeconds()))
                .onErrorResume(ex -> {
                    long stepElapsed = System.currentTimeMillis() - stepStart;
                    ToolPipelineResult.StepResult sr = ToolPipelineResult.StepResult.builder()
                            .stepId(step.getStepId())
                            .toolName(step.getToolName())
                            .success(false)
                            .error(ex.getMessage())
                            .elapsedMs(stepElapsed)
                            .build();
                    stepResults.put(step.getStepId(), sr);

                    log.warn("[Pipeline:{}] Step {} failed in {}ms: {}",
                            pipeline.getPipelineId(), step.getStepId(), stepElapsed, ex.getMessage());

                    if (step.isFailFast()) {
                        return Mono.error(new RuntimeException(
                                "Pipeline step '" + step.getStepId() + "' failed: " + ex.getMessage(), ex));
                    }
                    return Mono.just(TOOL_FAILED_SENTINEL);
                })
                .flatMap(output -> {
                    if (output == TOOL_FAILED_SENTINEL) {
                        log.info("[Pipeline:{}] Step {} skipped (non-failFast), continuing to next",
                                pipeline.getPipelineId(), step.getStepId());
                        if (index + 1 < steps.size()) {
                            return executeSteps(pipeline, steps, stepResults, stepOutputs, index + 1);
                        }
                        return Mono.just(stepOutputs.getOrDefault("__last__", null));
                    }

                    long stepElapsed = System.currentTimeMillis() - stepStart;
                    Object extracted = extractOutput(output, step.getExtractField());
                    stepOutputs.put(step.getStepId(), extracted);

                    ToolPipelineResult.StepResult sr = ToolPipelineResult.StepResult.builder()
                            .stepId(step.getStepId())
                            .toolName(step.getToolName())
                            .success(true)
                            .output(extracted)
                            .elapsedMs(stepElapsed)
                            .build();
                    stepResults.put(step.getStepId(), sr);

                    log.info("[Pipeline:{}] Step {} completed in {}ms: output={}",
                            pipeline.getPipelineId(), step.getStepId(), stepElapsed,
                            extracted instanceof String ? ((String) extracted).substring(0, Math.min(((String) extracted).length(), 80)) : extracted);

                    return executeSteps(pipeline, steps, stepResults, stepOutputs, index + 1);
                });
    }

    /**
     * 解析参数中的 ${stepId.fieldName} 引用。
     */
    Map<String, Object> resolveArguments(Map<String, Object> arguments, Map<String, Object> stepOutputs) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                resolved.put(entry.getKey(), resolveRefs((String) value, stepOutputs));
            } else {
                resolved.put(entry.getKey(), value);
            }
        }
        return resolved;
    }

    /**
     * 解析单个字符串值中的引用。
     */
    String resolveRefs(String value, Map<String, Object> stepOutputs) {
        Matcher matcher = REF_PATTERN.matcher(value);
        if (!matcher.find()) {
            return value;
        }
        matcher.reset();

        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String stepId = matcher.group(1);
            String fieldName = matcher.group(2);
            Object stepOutput = stepOutputs.get(stepId);
            String replacement = resolveField(stepOutput, fieldName);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 从步骤输出中提取指定字段。
     */
    @SuppressWarnings("unchecked")
    private String resolveField(Object output, String fieldName) {
        if (output == null) return "";

        if (fieldName == null || fieldName.isEmpty()) {
            return output instanceof String ? (String) output : String.valueOf(output);
        }

        if (output instanceof Map) {
            Object fieldValue = ((Map<String, Object>) output).get(fieldName);
            return fieldValue != null ? String.valueOf(fieldValue) : "";
        }

        return String.valueOf(output);
    }

    /**
     * 从工具输出中提取指定字段。
     */
    @SuppressWarnings("unchecked")
    private Object extractOutput(Object rawOutput, String extractField) {
        if (extractField == null || extractField.isEmpty()) {
            return rawOutput;
        }

        if (rawOutput instanceof Map) {
            return ((Map<String, Object>) rawOutput).getOrDefault(extractField, rawOutput);
        }

        return rawOutput;
    }

    private Object getLastOutput(Map<String, Object> stepOutputs) {
        if (stepOutputs.isEmpty()) return null;
        Object[] values = stepOutputs.values().toArray();
        return values[values.length - 1];
    }
}