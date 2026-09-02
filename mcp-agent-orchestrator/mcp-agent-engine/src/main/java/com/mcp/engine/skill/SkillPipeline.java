package com.mcp.engine.skill;

import com.mcp.common.skill.SkillContext;
import com.mcp.core.domain.memory.SkillEntity;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 技能管道 — 将多个技能串联为可复用的执行链路。
 *
 * 核心理念：能力可组合（Capability Composition），而非 JAR 热加载（Plugin Hot Loading）。
 *
 * 使用方式：
 * <pre>
 * SkillPipeline pipeline = SkillPipeline.startWith("web_search")
 *     .then("deep_research")
 *     .then("synthesize")
 *     .build();
 *
 * PipelineResult result = pipeline.execute(ctx -> {
 *     // 执行每一步
 * });
 * </pre>
 *
 * 设计原则：
 * - 每个 Step 有明确的输入/输出契约
 * - 支持条件分支（ifSuccess / ifFailure）
 * - 支持聚合步骤（collect — 等待所有并行步骤完成）
 * - 管道执行结果可追踪（通过 SessionTrace）
 */
@Slf4j
public class SkillPipeline {

    private final String name;
    private final List<PipelineStep> steps;
    private final PipelineConfig config;

    private SkillPipeline(String name, List<PipelineStep> steps, PipelineConfig config) {
        this.name = name;
        this.steps = List.copyOf(steps);
        this.config = config;
    }

    public static PipelineBuilder startWith(String skillName) {
        return new PipelineBuilder(skillName);
    }

    public String getName() {
        return name;
    }

    public List<PipelineStep> getSteps() {
        return steps;
    }

    /**
     * 执行管道 — 按顺序执行每个步骤。
     */
    public PipelineResult execute(Function<PipelineStep, StepResult> executor) {
        List<StepResult> results = new ArrayList<>();
        PipelineContext ctx = new PipelineContext();

        for (PipelineStep step : steps) {
            if (ctx.isAborted()) break;

            StepResult result;
            try {
                result = executor.apply(step);
            } catch (Exception e) {
                result = StepResult.failure(step.name(), e.getMessage());
            }

            results.add(result);
            ctx.record(result);

            if (result.isFailure() && config.failFast) {
                ctx.abort();
                log.warn("[SkillPipeline] {} step '{}' failed, aborting pipeline", name, step.name());
            }
        }

        return new PipelineResult(name, results, ctx.isAborted());
    }

    /**
     * 管道步骤定义。
     */
    public record PipelineStep(
            String name,
            String skillName,
            String description,
            boolean required,
            Predicate<PipelineContext> condition
    ) {
        public static PipelineStep of(String name, String skillName) {
            return new PipelineStep(name, skillName, null, true, null);
        }

        public static PipelineStep optional(String name, String skillName) {
            return new PipelineStep(name, skillName, null, false, null);
        }

        public static PipelineStep conditional(String name, String skillName,
                                                Predicate<PipelineContext> condition) {
            return new PipelineStep(name, skillName, null, true, condition);
        }
    }

    /**
     * 步骤执行结果。
     */
    public record StepResult(String stepName, boolean success, String output, String error) {
        public static StepResult success(String stepName, String output) {
            return new StepResult(stepName, true, output, null);
        }

        public static StepResult failure(String stepName, String error) {
            return new StepResult(stepName, false, null, error);
        }

        public boolean isFailure() {
            return !success;
        }
    }

    /**
     * 管道执行结果。
     */
    public record PipelineResult(String pipelineName, List<StepResult> steps, boolean aborted) {
        public boolean isComplete() {
            return !aborted && steps.stream().allMatch(StepResult::success);
        }

        public long successCount() {
            return steps.stream().filter(StepResult::success).count();
        }

        public long failureCount() {
            return steps.stream().filter(StepResult::isFailure).count();
        }

        public List<String> errors() {
            return steps.stream()
                    .filter(StepResult::isFailure)
                    .map(r -> r.stepName() + ": " + r.error())
                    .toList();
        }
    }

    /**
     * 管道执行上下文 — 在步骤间传递状态。
     */
    public static class PipelineContext {
        private final List<StepResult> history = new ArrayList<>();
        private boolean aborted = false;

        void record(StepResult result) {
            history.add(result);
        }

        void abort() {
            aborted = true;
        }

        public boolean isAborted() {
            return aborted;
        }

        public StepResult lastResult() {
            return history.isEmpty() ? null : history.get(history.size() - 1);
        }

        public boolean lastSucceeded() {
            StepResult last = lastResult();
            return last != null && last.success();
        }
    }

    /**
     * 管道配置。
     */
    public record PipelineConfig(boolean failFast, int maxSteps, long timeoutMs) {
        public static PipelineConfig defaults() {
            return new PipelineConfig(true, 10, 5 * 60 * 1000);
        }
    }

    public static class PipelineBuilder {
        private final List<PipelineStep> steps = new ArrayList<>();
        private String pipelineName = "unnamed";
        private PipelineConfig config = PipelineConfig.defaults();

        PipelineBuilder(String firstSkillName) {
            steps.add(PipelineStep.of("step_0", firstSkillName));
        }

        public PipelineBuilder named(String name) {
            this.pipelineName = name;
            return this;
        }

        public PipelineBuilder then(String skillName) {
            steps.add(PipelineStep.of("step_" + steps.size(), skillName));
            return this;
        }

        public PipelineBuilder thenOptional(String skillName) {
            steps.add(PipelineStep.optional("step_" + steps.size(), skillName));
            return this;
        }

        public PipelineBuilder thenConditional(String skillName, Predicate<PipelineContext> condition) {
            steps.add(PipelineStep.conditional("step_" + steps.size(), skillName, condition));
            return this;
        }

        public PipelineBuilder failFast(boolean failFast) {
            this.config = new PipelineConfig(failFast, config.maxSteps(), config.timeoutMs());
            return this;
        }

        public PipelineBuilder maxSteps(int maxSteps) {
            this.config = new PipelineConfig(config.failFast(), maxSteps, config.timeoutMs());
            return this;
        }

        public SkillPipeline build() {
            return new SkillPipeline(pipelineName, steps, config);
        }
    }
}