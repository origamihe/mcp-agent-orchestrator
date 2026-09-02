package com.mcp.engine.loop;

import com.mcp.engine.trace.SessionTrace;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;

/**
 * Agent 循环上下文 — 定义循环的终止条件、最大迭代数、状态机。
 *
 * 终止条件优先级（任一满足即终止）：
 * 1. 达到最大迭代轮次
 * 2. 满足自定义终止条件
 * 3. 超时
 * 4. 错误次数达到上限
 *
 * 设计原则：
 * - Loop 是 Agent 能力放大器，不是基础。只有单次执行稳定后，Loop 才有价值。
 * - 每次迭代都会通过 SessionTrace 记录事件，确保可追溯。
 * - 最多连续错误次数限制，防止"自动重复犯错"。
 */
public class LoopContext {

    private final int maxRounds;
    private final Duration timeout;
    private final int maxConsecutiveErrors;
    private final Predicate<LoopIterationResult> terminationCondition;

    private final Instant startTime;
    private int currentRound;
    private int consecutiveErrors;
    private boolean terminated;

    private LoopContext(Builder builder) {
        this.maxRounds = builder.maxRounds;
        this.timeout = builder.timeout;
        this.maxConsecutiveErrors = builder.maxConsecutiveErrors;
        this.terminationCondition = builder.terminationCondition;
        this.startTime = Instant.now();
        this.currentRound = 0;
        this.consecutiveErrors = 0;
        this.terminated = false;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean shouldContinue() {
        if (terminated) return false;
        if (currentRound >= maxRounds) return false;
        if (consecutiveErrors >= maxConsecutiveErrors) return false;
        if (Duration.between(startTime, Instant.now()).compareTo(timeout) > 0) return false;
        return true;
    }

    public void recordSuccess() {
        currentRound++;
        consecutiveErrors = 0;
    }

    public void recordError() {
        currentRound++;
        consecutiveErrors++;
    }

    public void markTerminated() {
        this.terminated = true;
    }

    public boolean checkTermination(LoopIterationResult result) {
        if (terminationCondition != null && terminationCondition.test(result)) {
            terminated = true;
            return true;
        }
        return false;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public int getConsecutiveErrors() {
        return consecutiveErrors;
    }

    public Duration getElapsed() {
        return Duration.between(startTime, Instant.now());
    }

    /**
     * 单次循环迭代的结果。
     */
    public record LoopIterationResult(
            boolean success,
            String output,
            String error,
            boolean hasToolCalls,
            int toolCallCount
    ) {
        public static LoopIterationResult success(String output, boolean hasToolCalls, int toolCallCount) {
            return new LoopIterationResult(true, output, null, hasToolCalls, toolCallCount);
        }

        public static LoopIterationResult error(String error) {
            return new LoopIterationResult(false, null, error, false, 0);
        }
    }

    public static class Builder {
        private int maxRounds = 10;
        private Duration timeout = Duration.ofMinutes(5);
        private int maxConsecutiveErrors = 3;
        private Predicate<LoopIterationResult> terminationCondition;

        public Builder maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxConsecutiveErrors(int maxConsecutiveErrors) {
            this.maxConsecutiveErrors = maxConsecutiveErrors;
            return this;
        }

        public Builder terminationCondition(Predicate<LoopIterationResult> condition) {
            this.terminationCondition = condition;
            return this;
        }

        public LoopContext build() {
            return new LoopContext(this);
        }
    }
}