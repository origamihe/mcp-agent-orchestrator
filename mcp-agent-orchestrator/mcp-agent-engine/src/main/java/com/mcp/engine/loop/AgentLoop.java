package com.mcp.engine.loop;

import com.mcp.engine.trace.SessionTrace;
import com.mcp.engine.trace.SessionTraceHolder;
import reactor.core.publisher.Mono;

/**
 * Agent 循环 — 可复用的自动化循环执行器。
 *
 * 将 Agent 执行包装为可配置的循环，支持：
 * - 最大迭代轮次
 * - 超时控制
 * - 连续错误上限
 * - 自定义终止条件
 * - 每轮的状态追踪（通过 SessionTrace）
 *
 * 使用方式：
 * <pre>
 * AgentLoop loop = AgentLoop.builder()
 *     .maxRounds(5)
 *     .timeout(Duration.ofMinutes(3))
 *     .build();
 *
 * Mono<LoopResult> result = loop.execute(
 *     "session-001",
 *     ctx -> searchAgent.execute(new LLMRequest(...))
 * );
 * </pre>
 *
 * 设计原则：
 * - Loop 是放大器，不是基础。先保证单次 Agent 执行正确，再加 Loop。
 * - 每次迭代都会通过 SessionTrace 记录状态转换事件。
 * - 达到错误上限后自动终止，防止"自动重复犯错"。
 */
@FunctionalInterface
public interface AgentLoop {

    /**
     * 执行一次迭代。
     *
     * @param ctx    循环上下文
     * @param action 单次 Agent 执行逻辑
     * @return 迭代结果
     */
    Mono<LoopContext.LoopIterationResult> executeIteration(
            LoopContext ctx,
            java.util.function.Function<LoopContext, Mono<LoopContext.LoopIterationResult>> action);

    /**
     * 执行完整的 Agent 循环。
     */
    default Mono<LoopResult> execute(
            String sessionId,
            java.util.function.Function<LoopContext, Mono<LoopContext.LoopIterationResult>> action) {

        LoopContext ctx = LoopContext.builder()
                .maxRounds(10)
                .timeout(java.time.Duration.ofMinutes(5))
                .maxConsecutiveErrors(3)
                .build();

        return executeLoop(ctx, action);
    }

    /**
     * 执行完整的 Agent 循环（自定义 LoopContext）。
     */
    default Mono<LoopResult> executeLoop(
            LoopContext ctx,
            java.util.function.Function<LoopContext, Mono<LoopContext.LoopIterationResult>> action) {

        return executeLoopInternal(ctx, action, LoopStateMachine.initial(), 0);
    }

    private Mono<LoopResult> executeLoopInternal(
            LoopContext ctx,
            java.util.function.Function<LoopContext, Mono<LoopContext.LoopIterationResult>> action,
            LoopStateMachine state,
            int depth) {

        if (!ctx.shouldContinue()) {
            return Mono.just(LoopResult.completed(
                    ctx.getCurrentRound(),
                    ctx.getElapsed().toMillis(),
                    state == LoopStateMachine.ERROR));
        }

        SessionTrace trace = SessionTraceHolder.currentOrNull();
        if (trace != null) {
            trace.recordToolDecision(
                    "LOOP_ROUND_" + ctx.getCurrentRound(),
                    true,
                    ctx.getCurrentRound());
        }

        return action.apply(ctx)
                .flatMap(iterationResult -> {
                    if (iterationResult.success()) {
                        ctx.recordSuccess();
                    } else {
                        ctx.recordError();
                    }

                    boolean shouldTerminate = ctx.checkTermination(iterationResult);

                    if (shouldTerminate) {
                        return Mono.just(LoopResult.completed(
                                ctx.getCurrentRound(),
                                ctx.getElapsed().toMillis(),
                                false));
                    }

                    if (!ctx.shouldContinue()) {
                        return Mono.just(LoopResult.completed(
                                ctx.getCurrentRound(),
                                ctx.getElapsed().toMillis(),
                                ctx.getConsecutiveErrors() > 0));
                    }

                    LoopStateMachine nextState = LoopStateMachine.next(
                            state,
                            iterationResult.hasToolCalls(),
                            !iterationResult.success());

                    return executeLoopInternal(ctx, action, nextState, depth + 1);
                })
                .onErrorResume(ex -> {
                    ctx.recordError();
                    if (trace != null) {
                        trace.recordContractViolation("LoopError",
                                "iteration_" + ctx.getCurrentRound(),
                                ex.getClass().getSimpleName(),
                                ex.getMessage());
                    }
                    return Mono.just(LoopResult.error(
                            ctx.getCurrentRound(),
                            ctx.getElapsed().toMillis(),
                            ex.getMessage()));
                });
    }

    /**
     * 循环执行结果。
     */
    record LoopResult(
            int totalRounds,
            long totalElapsedMs,
            boolean hasError,
            String errorMessage
    ) {
        public static LoopResult completed(int rounds, long elapsedMs, boolean hasError) {
            return new LoopResult(rounds, elapsedMs, hasError, null);
        }

        public static LoopResult error(int rounds, long elapsedMs, String errorMessage) {
            return new LoopResult(rounds, elapsedMs, true, errorMessage);
        }

        public boolean isSuccess() {
            return !hasError && errorMessage == null;
        }
    }

    /**
     * 创建默认的 AgentLoop 实例。
     */
    static AgentLoop create() {
        return (ctx, action) -> action.apply(ctx);
    }
}