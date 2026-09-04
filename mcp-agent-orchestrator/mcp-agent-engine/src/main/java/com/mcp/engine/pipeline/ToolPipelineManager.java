package com.mcp.engine.pipeline;

import com.mcp.common.pipeline.PipelineDefinition;
import com.mcp.common.pipeline.PipelineResult;
import com.mcp.common.pipeline.PipelineStatus;
import com.mcp.common.pipeline.PipelineStep;
import com.mcp.common.pipeline.StepResult;
import com.mcp.tools.executor.ToolExecutor;
import com.mcp.tools.model.ToolExecutionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * ToolPipelineManager — Agent Runtime 工具流水线管理器。
 *
 * 核心职责：
 * 1. 流水线注册 — 注册/管理 PipelineDefinition
 * 2. 顺序执行 — 按依赖顺序执行步骤
 * 3. 数据流 — 步骤 N 的输出自动映射到步骤 N+1 的输入
 * 4. 并行执行 — 独立步骤（无依赖关系）并行执行
 * 5. 错误处理 — 失败自动回退到 fallbackTool
 * 6. 可观测性 — 每个步骤的输入/输出/耗时/状态全程追踪
 *
 * 示例流水线（搜索→分析→报告）：
 * <pre>
 * PipelineDefinition pipeline = PipelineDefinition.builder()
 *     .name("搜索分析报告")
 *     .addStep(PipelineStep.builder().id("search").toolName("web_search").build())
 *     .addStep(PipelineStep.builder().id("analyze").toolName("analyze_content")
 *         .dependsOn("search").inputMapping("content", "search.result").build())
 *     .addStep(PipelineStep.builder().id("report").toolName("generate_report")
 *         .dependsOn("analyze").build())
 *     .build();
 * </pre>
 */
@Slf4j
@Service
public class ToolPipelineManager {

    private final ToolExecutor toolExecutor;
    private final Map<String, PipelineDefinition> pipelines = new ConcurrentHashMap<>();
    private final List<PipelineResult> recentResults = new CopyOnWriteArrayList<>();
    private final List<Consumer<PipelineResult>> listeners = new CopyOnWriteArrayList<>();

    private static final int MAX_RECENT_RESULTS = 200;

    public ToolPipelineManager(ToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    // ==================== 流水线注册 ====================

    public void registerPipeline(PipelineDefinition pipeline) {
        pipelines.put(pipeline.getId(), pipeline);
        log.info("[ToolPipeline] Registered pipeline: {} ({} steps)", pipeline.getName(), pipeline.getSteps().size());
    }

    public void unregisterPipeline(String pipelineId) {
        pipelines.remove(pipelineId);
        log.info("[ToolPipeline] Unregistered pipeline: {}", pipelineId);
    }

    public PipelineDefinition getPipeline(String pipelineId) {
        return pipelines.get(pipelineId);
    }

    public List<PipelineDefinition> getAllPipelines() {
        return new ArrayList<>(pipelines.values());
    }

    // ==================== 流水线执行 ====================

    /**
     * 执行流水线。
     *
     * @param pipelineId   流水线 ID
     * @param initialInput 初始输入参数（传递给第一个步骤）
     * @return 流水线执行结果
     */
    public Mono<PipelineResult> execute(String pipelineId, Map<String, Object> initialInput) {
        PipelineDefinition def = pipelines.get(pipelineId);
        if (def == null) {
            return Mono.error(new IllegalArgumentException("Pipeline not found: " + pipelineId));
        }
        return execute(def, initialInput);
    }

    /**
     * 执行流水线定义。
     */
    public Mono<PipelineResult> execute(PipelineDefinition def, Map<String, Object> initialInput) {
        Instant startTime = Instant.now();
        List<StepResult> stepResults = new ArrayList<>();
        Map<String, Object> context = new LinkedHashMap<>(initialInput != null ? initialInput : Map.of());

        log.info("[ToolPipeline] Executing pipeline: {} ({} steps)", def.getName(), def.getSteps().size());

        List<PipelineStep> steps = def.getSteps();
        if (steps.isEmpty()) {
            PipelineResult emptyResult = PipelineResult.success(def.getId(), def.getName(), List.of());
            emptyResult.setStartedAt(startTime);
            return Mono.just(emptyResult);
        }

        return executeSteps(steps, context, stepResults, def.isParallelizeIndependent())
                .then(Mono.fromCallable(() -> {
                    PipelineResult result = PipelineResult.success(def.getId(), def.getName(), stepResults);
                    result.setStartedAt(startTime);
                    recordResult(result);
                    notifyListeners(result);
                    log.info("[ToolPipeline] Pipeline completed: {} ({} steps, {}ms)",
                            def.getName(), stepResults.size(), result.getTotalDurationMs());
                    return result;
                }))
                .onErrorResume(error -> {
                    PipelineResult result = PipelineResult.failure(def.getId(), def.getName(), stepResults);
                    result.setStartedAt(startTime);
                    recordResult(result);
                    notifyListeners(result);
                    log.error("[ToolPipeline] Pipeline failed: {} - {}", def.getName(), error.getMessage());
                    return Mono.just(result);
                });
    }

    /**
     * 递归执行步骤，处理依赖关系。
     */
    private Mono<Void> executeSteps(List<PipelineStep> allSteps, Map<String, Object> context,
                                     List<StepResult> results, boolean parallelizeIndependent) {
        if (allSteps.isEmpty()) return Mono.empty();

        List<PipelineStep> ready = findReadySteps(allSteps, results);
        List<PipelineStep> remaining = new ArrayList<>(allSteps);
        remaining.removeAll(ready);

        if (ready.isEmpty()) {
            if (!remaining.isEmpty()) {
                return Mono.error(new RuntimeException("Circular dependency detected in pipeline"));
            }
            return Mono.empty();
        }

        if (parallelizeIndependent && ready.size() > 1) {
            return Flux.fromIterable(ready)
                    .flatMap(step -> executeStep(step, context, results))
                    .then()
                    .then(Mono.defer(() -> executeSteps(remaining, context, results, parallelizeIndependent)));
        }

        return Flux.fromIterable(ready)
                .concatMap(step -> executeStep(step, context, results))
                .then()
                .then(Mono.defer(() -> executeSteps(remaining, context, results, parallelizeIndependent)));
    }

    /**
     * 执行单个步骤。
     */
    private Mono<Void> executeStep(PipelineStep step, Map<String, Object> context, List<StepResult> results) {
        long stepStart = System.currentTimeMillis();

        Map<String, Object> resolvedArgs = resolveArguments(step, context, results);

        ToolExecutionRequest request = new ToolExecutionRequest();
        request.setToolName(step.getToolName());
        request.setArguments(resolvedArgs);

        return toolExecutor.execute(request)
                .map(toolResult -> {
                    long duration = System.currentTimeMillis() - stepStart;
                    Object output = toolResult.data();
                    StepResult sr = StepResult.success(step.getId(), step.getToolName(), output, duration);
                    results.add(sr);
                    context.put(step.getId(), output);

                    if (output instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> outputMap = (Map<String, Object>) output;
                        context.put(step.getId() + ".result", outputMap);
                        outputMap.forEach((k, v) -> context.put(step.getId() + "." + k, v));
                    } else {
                        context.put(step.getId() + ".result", output);
                    }

                    log.info("[ToolPipeline] Step completed: {} ({}ms)", step.getId(), duration);
                    return sr;
                })
                .onErrorResume(error -> {
                    if (step.getFallbackTool() != null && !step.getFallbackTool().isBlank()) {
                        log.warn("[ToolPipeline] Step failed, trying fallback: {} -> {}: {}",
                                step.getToolName(), step.getFallbackTool(), error.getMessage());
                        return executeFallback(step, context, stepStart, results);
                    }
                    long duration = System.currentTimeMillis() - stepStart;
                    StepResult sr = StepResult.failure(step.getId(), step.getToolName(), error.getMessage(), duration);
                    results.add(sr);
                    log.warn("[ToolPipeline] Step failed: {} ({}ms, error: {})", step.getId(), duration, error.getMessage());
                    return Mono.error(error);
                })
                .then();
    }

    /**
     * 执行备选工具。
     */
    private Mono<StepResult> executeFallback(PipelineStep step, Map<String, Object> context,
                                              long stepStart, List<StepResult> results) {
        Map<String, Object> resolvedArgs = resolveArguments(step, context, results);

        ToolExecutionRequest request = new ToolExecutionRequest();
        request.setToolName(step.getFallbackTool());
        request.setArguments(resolvedArgs);

        return toolExecutor.execute(request)
                .map(toolResult -> {
                    long duration = System.currentTimeMillis() - stepStart;
                    Object output = toolResult.data();
                    StepResult sr = StepResult.success(step.getId(), step.getFallbackTool(), output, duration);
                    sr.setFallbackToolUsed(step.getFallbackTool());
                    results.add(sr);
                    context.put(step.getId(), output);
                    log.info("[ToolPipeline] Fallback succeeded: {} -> {} ({}ms)",
                            step.getId(), step.getFallbackTool(), duration);
                    return sr;
                })
                .onErrorResume(fbError -> {
                    long duration = System.currentTimeMillis() - stepStart;
                    String errMsg = "Primary(" + step.getToolName() + ") and fallback(" + step.getFallbackTool()
                            + ") both failed: " + fbError.getMessage();
                    StepResult sr = StepResult.failure(step.getId(), step.getToolName(), errMsg, duration);
                    sr.setFallbackToolUsed(step.getFallbackTool());
                    results.add(sr);
                    return Mono.error(new RuntimeException(errMsg));
                });
    }

    /**
     * 解析步骤参数：合并静态参数和上游输出映射。
     */
    Map<String, Object> resolveArguments(PipelineStep step, Map<String, Object> context,
                                          List<StepResult> results) {
        Map<String, Object> resolved = new LinkedHashMap<>(step.getStaticArgs());

        if (step.getInputMapping() != null) {
            for (Map.Entry<String, String> mapping : step.getInputMapping().entrySet()) {
                String paramName = mapping.getKey();
                String sourcePath = mapping.getValue();
                Object value = resolvePath(sourcePath, context, results);
                if (value != null) {
                    resolved.put(paramName, value);
                }
            }
        }

        return resolved;
    }

    /**
     * 通过路径表达式解析值。
     * 支持 "stepId.field" 或 "stepId" 格式。
     */
    Object resolvePath(String path, Map<String, Object> context, List<StepResult> results) {
        if (path == null || path.isBlank()) return null;

        String[] parts = path.split("\\.", 2);
        String stepId = parts[0];

        Object stepOutput = context.get(stepId);
        if (stepOutput == null) {
            for (StepResult sr : results) {
                if (sr.getStepId().equals(stepId) && sr.isSuccess()) {
                    stepOutput = sr.getOutput();
                    break;
                }
            }
        }

        if (stepOutput == null || parts.length == 1) return stepOutput;

        String field = parts[1];
        Object fieldValue = context.get(path);
        if (fieldValue != null) return fieldValue;

        if (stepOutput instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) stepOutput;
            return map.get(field);
        }

        return null;
    }

    /**
     * 找到所有依赖已满足的步骤（没有依赖或依赖步骤已完成）。
     */
    List<PipelineStep> findReadySteps(List<PipelineStep> steps, List<StepResult> results) {
        List<PipelineStep> ready = new ArrayList<>();
        for (PipelineStep step : steps) {
            if (results.stream().anyMatch(r -> r.getStepId().equals(step.getId()))) {
                continue;
            }
            if (step.getDependsOn().isEmpty() || step.getDependsOn().stream()
                    .allMatch(dep -> results.stream().anyMatch(r -> r.getStepId().equals(dep) && r.isSuccess()))) {
                ready.add(step);
            }
        }
        return ready;
    }

    // ==================== 历史查询 ====================

    public List<PipelineResult> getRecentResults(int limit) {
        int size = recentResults.size();
        int from = Math.max(0, size - limit);
        return recentResults.subList(from, size);
    }

    public List<PipelineResult> getAllResults() {
        return new ArrayList<>(recentResults);
    }

    // ==================== 监听器 ====================

    public void addListener(Consumer<PipelineResult> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<PipelineResult> listener) {
        listeners.remove(listener);
    }

    private void recordResult(PipelineResult result) {
        recentResults.add(result);
        while (recentResults.size() > MAX_RECENT_RESULTS) {
            recentResults.remove(0);
        }
    }

    private void notifyListeners(PipelineResult result) {
        for (Consumer<PipelineResult> listener : listeners) {
            try {
                listener.accept(result);
            } catch (Exception e) {
                log.warn("[ToolPipeline] Listener error: {}", e.getMessage());
            }
        }
    }
}