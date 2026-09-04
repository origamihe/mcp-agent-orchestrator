package com.mcp.engine.execution;

import com.mcp.common.channel.ContextRequirement;
import com.mcp.common.identity.MemoryIdentity;

import java.time.Duration;
import java.util.UUID;

/**
 * 执行计划 — P1 核心数据结构，替代 if/else 路由链。
 *
 * ExecutionPlan 是"怎么做"的声明式描述，由 Orchestrator 产生，
 * 由 AgentRuntime 消费，替代当前代码中通过：
 * <pre>
 *   if (currentTask.startsWith("SEARCH:")) { ... }
 *   else if (currentTask.startsWith("DOCX_GENERATION:")) { ... }
 *   else { ... }
 * </pre>
 * 隐式决定的执行路径。
 *
 * 设计原则：
 * - 不可变 record，一次创建后不再修改
 * - executionId 用于全链路追踪
 * - 包含 Agent、Pipeline、Context、Tool、Memory、Timeout 六大策略
 */
public record ExecutionPlan(
        String executionId,
        ExecutionMode mode,
        String agentId,
        String pipelineId,
        ContextRequirement contextRequirement,
        ToolPolicy toolPolicy,
        MemoryPolicy memoryPolicy,
        TimeoutPolicy timeoutPolicy,
        MemoryIdentity identity
) {

    public static ExecutionPlanBuilder builder() {
        return new ExecutionPlanBuilder();
    }

    public enum ExecutionMode {
        DIRECT,
        PIPELINE,
        AGENT,
        MULTI_AGENT,
        RECALL_HISTORY,
        FAST_PATH
    }

    public record ToolPolicy(
            boolean allowSearch,
            boolean allowFileWrite,
            boolean allowFileRead,
            boolean allowCodeExecution,
            int maxToolCalls
    ) {
        public static final ToolPolicy DEFAULT = new ToolPolicy(false, false, true, false, 10);
        public static final ToolPolicy SEARCH_ALLOWED = new ToolPolicy(true, false, true, false, 20);
        public static final ToolPolicy CODE_FULL = new ToolPolicy(true, true, true, true, 50);
        public static final ToolPolicy READ_ONLY = new ToolPolicy(false, false, true, false, 5);
    }

    public record MemoryPolicy(
            boolean readEnabled,
            boolean writeEnabled,
            int maxMemoryTokens,
            String memoryType
    ) {
        public static final MemoryPolicy DEFAULT = new MemoryPolicy(true, true, 1000, "SESSION");
        public static final MemoryPolicy DISABLED = new MemoryPolicy(false, false, 0, "NONE");
        public static final MemoryPolicy READ_ONLY = new MemoryPolicy(true, false, 1000, "SESSION");
    }

    public record TimeoutPolicy(
            Duration executionTimeout,
            Duration llmTimeout,
            Duration toolTimeout,
            Duration pipelineTimeout,
            Duration reserveTimeout
    ) {
        public static final TimeoutPolicy DEFAULT = new TimeoutPolicy(
                Duration.ofSeconds(120),
                Duration.ofSeconds(40),
                Duration.ofSeconds(30),
                Duration.ofSeconds(20),
                Duration.ofSeconds(30)
        );

        public static final TimeoutPolicy SHORT = new TimeoutPolicy(
                Duration.ofSeconds(30),
                Duration.ofSeconds(15),
                Duration.ofSeconds(10),
                Duration.ofSeconds(5),
                Duration.ofSeconds(0)
        );

        public static final TimeoutPolicy LONG = new TimeoutPolicy(
                Duration.ofSeconds(300),
                Duration.ofSeconds(120),
                Duration.ofSeconds(60),
                Duration.ofSeconds(60),
                Duration.ofSeconds(60)
        );
    }

    public static final class ExecutionPlanBuilder {
        private String executionId = UUID.randomUUID().toString();
        private ExecutionMode mode = ExecutionMode.DIRECT;
        private String agentId;
        private String pipelineId;
        private ContextRequirement contextRequirement = ContextRequirement.NONE;
        private ToolPolicy toolPolicy = ToolPolicy.DEFAULT;
        private MemoryPolicy memoryPolicy = MemoryPolicy.DEFAULT;
        private TimeoutPolicy timeoutPolicy = TimeoutPolicy.DEFAULT;
        private MemoryIdentity identity;

        public ExecutionPlanBuilder executionId(String executionId) { this.executionId = executionId; return this; }
        public ExecutionPlanBuilder mode(ExecutionMode mode) { this.mode = mode; return this; }
        public ExecutionPlanBuilder agentId(String agentId) { this.agentId = agentId; return this; }
        public ExecutionPlanBuilder pipelineId(String pipelineId) { this.pipelineId = pipelineId; return this; }
        public ExecutionPlanBuilder contextRequirement(ContextRequirement cr) { this.contextRequirement = cr; return this; }
        public ExecutionPlanBuilder toolPolicy(ToolPolicy tp) { this.toolPolicy = tp; return this; }
        public ExecutionPlanBuilder memoryPolicy(MemoryPolicy mp) { this.memoryPolicy = mp; return this; }
        public ExecutionPlanBuilder timeoutPolicy(TimeoutPolicy tp) { this.timeoutPolicy = tp; return this; }
        public ExecutionPlanBuilder identity(MemoryIdentity identity) { this.identity = identity; return this; }

        public ExecutionPlan build() {
            if (identity == null) throw new IllegalStateException("identity is required");
            return new ExecutionPlan(executionId, mode, agentId, pipelineId, contextRequirement,
                    toolPolicy, memoryPolicy, timeoutPolicy, identity);
        }
    }
}