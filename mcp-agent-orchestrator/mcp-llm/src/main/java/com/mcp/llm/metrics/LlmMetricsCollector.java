package com.mcp.llm.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * LLM 调用指标采集器 — 采集 LLM 调用耗时、Token 用量、成功率等关键指标。
 *
 * 暴露的 Prometheus 指标：
 * - llm_call_duration_seconds: LLM 调用耗时分布
 * - llm_call_total: LLM 调用总次数（按 provider/model/status 分类）
 * - llm_token_usage_total: Token 用量（按 prompt/completion/total 分类）
 * - llm_stream_call_total: 流式调用总次数
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmMetricsCollector {

    private final MeterRegistry meterRegistry;

    private Timer llmCallDuration;
    private Counter llmCallSuccess;
    private Counter llmCallFailure;
    private Counter llmTokenPrompt;
    private Counter llmTokenCompletion;
    private Counter llmStreamCalls;

    @PostConstruct
    public void init() {
        llmCallDuration = Timer.builder("llm.call.duration")
                .description("LLM call duration in seconds")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        llmCallSuccess = Counter.builder("llm.call.total")
                .description("Total LLM call count")
                .tag("status", "success")
                .register(meterRegistry);

        llmCallFailure = Counter.builder("llm.call.total")
                .description("Total LLM call count")
                .tag("status", "failure")
                .register(meterRegistry);

        llmTokenPrompt = Counter.builder("llm.token.usage")
                .description("LLM token usage")
                .tag("type", "prompt")
                .register(meterRegistry);

        llmTokenCompletion = Counter.builder("llm.token.usage")
                .description("LLM token usage")
                .tag("type", "completion")
                .register(meterRegistry);

        llmStreamCalls = Counter.builder("llm.stream.calls")
                .description("LLM streaming call count")
                .register(meterRegistry);

        log.info("[LlmMetrics] Metrics collector initialized");
    }

    public void recordCallDuration(long durationMs) {
        llmCallDuration.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordSuccess() {
        llmCallSuccess.increment();
    }

    public void recordFailure() {
        llmCallFailure.increment();
    }

    public void recordTokenUsage(long promptTokens, long completionTokens) {
        if (promptTokens > 0) {
            llmTokenPrompt.increment(promptTokens);
        }
        if (completionTokens > 0) {
            llmTokenCompletion.increment(completionTokens);
        }
    }

    public void recordStreamCall() {
        llmStreamCalls.increment();
    }
}