package com.mcp.engine.test.fixtures;

import com.mcp.common.channel.ContextRequirement;
import com.mcp.common.identity.MemoryIdentity;
import com.mcp.engine.execution.ExecutionPlan;

/**
 * ExecutionPlan 测试 Fixture。
 *
 * 提供最小合法 ExecutionPlan，避免测试通过 null 绕过 Policy 检查。
 * 生产代码不感知此 Fixture 类。
 */
public final class ExecutionPlanFixtures {

    private ExecutionPlanFixtures() {}

    private static final MemoryIdentity TEST_IDENTITY =
            new MemoryIdentity("test", "test-session", "test-user", null, null);

    /**
     * 创建最小合法 ExecutionPlan。
     * ToolPolicy 使用 DEFAULT（allowSearch=false, allowFileRead=true, allowFileWrite=false）。
     */
    public static ExecutionPlan minimal() {
        return ExecutionPlan.builder()
                .identity(TEST_IDENTITY)
                .build();
    }

    /**
     * 创建允许搜索的 ExecutionPlan。
     */
    public static ExecutionPlan searchAllowed() {
        return ExecutionPlan.builder()
                .identity(TEST_IDENTITY)
                .toolPolicy(ExecutionPlan.ToolPolicy.SEARCH_ALLOWED)
                .build();
    }

    /**
     * 创建只读的 ExecutionPlan。
     */
    public static ExecutionPlan readOnly() {
        return ExecutionPlan.builder()
                .identity(TEST_IDENTITY)
                .toolPolicy(ExecutionPlan.ToolPolicy.READ_ONLY)
                .build();
    }

    /**
     * 创建自定义 ToolPolicy 的 ExecutionPlan。
     */
    public static ExecutionPlan withToolPolicy(ExecutionPlan.ToolPolicy toolPolicy) {
        return ExecutionPlan.builder()
                .identity(TEST_IDENTITY)
                .toolPolicy(toolPolicy)
                .build();
    }
}