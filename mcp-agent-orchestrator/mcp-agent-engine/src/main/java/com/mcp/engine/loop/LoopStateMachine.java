package com.mcp.engine.loop;

/**
 * ReAct 循环状态机 — Think → Act → Observe → Decide。
 *
 * 每个状态的含义：
 * <pre>
 * THINK   — Agent 分析当前上下文，决定下一步行动
 * ACT     — Agent 执行工具调用
 * OBSERVE — Agent 观察工具执行结果
 * DECIDE  — Agent 判断：继续循环 / 返回最终答案
 * DONE    — 循环结束
 * ERROR   — 发生错误，进入错误处理
 * </pre>
 *
 * 这是 ReAct (Reasoning + Acting) 模式的标准实现。
 * 与 SearchAgent 的 reactLoop 共享相同的设计理念，
 * 但被提取为独立的可复用状态机，供不同 Agent 使用。
 */
public enum LoopStateMachine {

    THINK,
    ACT,
    OBSERVE,
    DECIDE,
    DONE,
    ERROR;

    /**
     * 状态转换 — 根据当前状态和条件决定下一个状态。
     */
    public static LoopStateMachine next(LoopStateMachine current, boolean hasToolCalls, boolean hasError) {
        return switch (current) {
            case THINK -> hasError ? ERROR : (hasToolCalls ? ACT : DONE);
            case ACT -> hasError ? ERROR : OBSERVE;
            case OBSERVE -> hasError ? ERROR : DECIDE;
            case DECIDE -> hasToolCalls ? ACT : DONE;
            case ERROR -> DONE;
            case DONE -> DONE;
        };
    }

    /**
     * 初始状态。
     */
    public static LoopStateMachine initial() {
        return THINK;
    }

    /**
     * 是否已终止。
     */
    public boolean isTerminal() {
        return this == DONE || this == ERROR;
    }
}