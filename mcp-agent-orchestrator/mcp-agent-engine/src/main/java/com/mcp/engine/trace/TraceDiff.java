package com.mcp.engine.trace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trace 对比器 — 比较两次 TraceRecord（如旧模型 vs 新模型），检测退化。
 *
 * 对比维度：
 * - Latency：延迟是否显著增加
 * - Output：输出是否退化（为空、过短、语义丢失）
 * - LayerCount：上下文层数是否异常
 * - Token：Token 消耗是否异常
 */
public class TraceDiff {

    /**
     * 对比两个 TraceRecord，生成对比报告。
     *
     * @param baseline 基准 Trace（旧模型/旧版本）
     * @param current  当前 Trace（新模型/新版本）
     * @return 对比结果
     */
    public static Result compare(TraceRecord baseline, TraceRecord current) {
        if (baseline == null || current == null) {
            return Result.builder()
                    .passed(false)
                    .summary("基准或当前 Trace 为空，无法对比")
                    .build();
        }

        List<String> regressions = new ArrayList<>();
        List<String> improvements = new ArrayList<>();
        Map<String, Object> details = new LinkedHashMap<>();

        compareLatency(baseline, current, regressions, improvements, details);
        compareOutput(baseline, current, regressions, improvements, details);
        compareLayerCount(baseline, current, regressions, improvements, details);

        boolean passed = regressions.isEmpty();

        String summary = passed
                ? "无退化检测到" + (improvements.isEmpty() ? "" : "，" + improvements.size() + " 项改善")
                : "检测到 " + regressions.size() + " 项退化";

        return Result.builder()
                .passed(passed)
                .summary(summary)
                .regressions(regressions)
                .improvements(improvements)
                .details(details)
                .baselineTraceId(baseline.traceId())
                .currentTraceId(current.traceId())
                .build();
    }

    private static void compareLatency(TraceRecord baseline, TraceRecord current,
                                        List<String> regressions, List<String> improvements,
                                        Map<String, Object> details) {
        long baselineMs = baseline.elapsedMs();
        long currentMs = current.elapsedMs();
        details.put("baselineLatencyMs", baselineMs);
        details.put("currentLatencyMs", currentMs);

        if (baselineMs > 0 && currentMs > baselineMs * 2) {
            regressions.add(String.format("延迟显著增加: %dms → %dms (+%.0f%%)",
                    baselineMs, currentMs, 100.0 * (currentMs - baselineMs) / baselineMs));
        } else if (baselineMs > 0 && currentMs < baselineMs * 0.5) {
            improvements.add(String.format("延迟显著降低: %dms → %dms (-%.0f%%)",
                    baselineMs, currentMs, 100.0 * (baselineMs - currentMs) / baselineMs));
        }
    }

    private static void compareOutput(TraceRecord baseline, TraceRecord current,
                                       List<String> regressions, List<String> improvements,
                                       Map<String, Object> details) {
        boolean hasLlmOutput = baseline.llmOutput() != null || current.llmOutput() != null;

        if (hasLlmOutput) {
            compareLlmOutput(baseline, current, regressions, improvements, details);
        } else {
            compareRenderedPrompt(baseline, current, regressions, improvements, details);
        }
    }

    private static void compareLlmOutput(TraceRecord baseline, TraceRecord current,
                                          List<String> regressions, List<String> improvements,
                                          Map<String, Object> details) {
        String baselineOutput = baseline.llmOutput();
        String currentOutput = current.llmOutput();

        details.put("baselineLlmOutputLength", baselineOutput != null ? baselineOutput.length() : 0);
        details.put("currentLlmOutputLength", currentOutput != null ? currentOutput.length() : 0);

        if (currentOutput == null || currentOutput.isBlank()) {
            regressions.add("LLM 输出为空 — 严重退化");
            return;
        }

        if (baselineOutput == null || baselineOutput.isBlank()) {
            return;
        }

        if (currentOutput.length() < baselineOutput.length() * 0.2) {
            regressions.add(String.format("LLM 输出显著缩短: %d chars → %d chars (-%.0f%%)",
                    baselineOutput.length(), currentOutput.length(),
                    100.0 * (baselineOutput.length() - currentOutput.length()) / baselineOutput.length()));
        }

        if (currentOutput.length() > baselineOutput.length() * 3.0) {
            improvements.add(String.format("LLM 输出更详细: %d chars → %d chars (+%.0f%%)",
                    baselineOutput.length(), currentOutput.length(),
                    100.0 * (currentOutput.length() - baselineOutput.length()) / baselineOutput.length()));
        }
    }

    private static void compareRenderedPrompt(TraceRecord baseline, TraceRecord current,
                                               List<String> regressions, List<String> improvements,
                                               Map<String, Object> details) {
        String baselineOutput = baseline.renderedPrompt();
        String currentOutput = current.renderedPrompt();

        details.put("baselinePromptLength", baselineOutput != null ? baselineOutput.length() : 0);
        details.put("currentPromptLength", currentOutput != null ? currentOutput.length() : 0);

        if (currentOutput == null || currentOutput.isBlank()) {
            regressions.add("System Prompt 为空 — 严重退化");
            return;
        }

        if (baselineOutput == null || baselineOutput.isBlank()) {
            return;
        }

        if (currentOutput.length() < baselineOutput.length() * 0.3) {
            regressions.add(String.format("System Prompt 显著缩短: %d chars → %d chars (-%.0f%%)",
                    baselineOutput.length(), currentOutput.length(),
                    100.0 * (baselineOutput.length() - currentOutput.length()) / baselineOutput.length()));
        }

        if (currentOutput.length() > baselineOutput.length() * 1.5) {
            improvements.add(String.format("System Prompt 更丰富: %d chars → %d chars (+%.0f%%)",
                    baselineOutput.length(), currentOutput.length(),
                    100.0 * (currentOutput.length() - baselineOutput.length()) / baselineOutput.length()));
        }
    }

    private static void compareLayerCount(TraceRecord baseline, TraceRecord current,
                                           List<String> regressions, List<String> improvements,
                                           Map<String, Object> details) {
        int baselineLayers = baseline.layerCount();
        int currentLayers = current.layerCount();
        details.put("baselineLayerCount", baselineLayers);
        details.put("currentLayerCount", currentLayers);

        if (currentLayers == 0) {
            regressions.add("上下文层数为 0 — 所有层被过滤");
        } else if (baselineLayers > 0 && currentLayers < baselineLayers * 0.5) {
            regressions.add(String.format("上下文层数显著减少: %d → %d",
                    baselineLayers, currentLayers));
        }
    }

    /**
     * 对比结果。
     */
    public static class Result {
        private final boolean passed;
        private final String summary;
        private final List<String> regressions;
        private final List<String> improvements;
        private final Map<String, Object> details;
        private final String baselineTraceId;
        private final String currentTraceId;

        private Result(Builder builder) {
            this.passed = builder.passed;
            this.summary = builder.summary;
            this.regressions = builder.regressions;
            this.improvements = builder.improvements;
            this.details = builder.details;
            this.baselineTraceId = builder.baselineTraceId;
            this.currentTraceId = builder.currentTraceId;
        }

        public boolean passed() { return passed; }
        public String summary() { return summary; }
        public List<String> regressions() { return regressions; }
        public List<String> improvements() { return improvements; }
        public Map<String, Object> details() { return details; }
        public String baselineTraceId() { return baselineTraceId; }
        public String currentTraceId() { return currentTraceId; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private boolean passed;
            private String summary;
            private List<String> regressions = List.of();
            private List<String> improvements = List.of();
            private Map<String, Object> details = Map.of();
            private String baselineTraceId;
            private String currentTraceId;

            public Builder passed(boolean v) { this.passed = v; return this; }
            public Builder summary(String v) { this.summary = v; return this; }
            public Builder regressions(List<String> v) { this.regressions = v; return this; }
            public Builder improvements(List<String> v) { this.improvements = v; return this; }
            public Builder details(Map<String, Object> v) { this.details = v; return this; }
            public Builder baselineTraceId(String v) { this.baselineTraceId = v; return this; }
            public Builder currentTraceId(String v) { this.currentTraceId = v; return this; }

            public Result build() { return new Result(this); }
        }
    }
}