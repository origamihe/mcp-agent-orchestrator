package com.mcp.engine.test.fixtures;

import com.mcp.engine.execution.ExecutionState;

/**
 * ExecutionState 测试 Fixture。
 *
 * 提供最小合法 ExecutionState，避免测试通过 null 绕过状态追踪。
 * 生产代码不感知此 Fixture 类。
 */
public final class ExecutionStateFixtures {

    private ExecutionStateFixtures() {}

    private static final String TEST_EXECUTION_ID = "test-exec-001";

    /**
     * 创建最小合法 ExecutionState（PENDING 状态）。
     */
    public static ExecutionState minimal() {
        return new ExecutionState(TEST_EXECUTION_ID);
    }

    /**
     * 创建指定 ID 的 ExecutionState。
     */
    public static ExecutionState withId(String executionId) {
        return new ExecutionState(executionId);
    }
}