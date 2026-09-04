package com.mcp.tools.pipeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 工具管道定义 — 将多个工具调用串联为确定性工作流。
 * <p>
 * 与 LLM 驱动的 ReAct 循环不同，Pipeline 是预定义的确定性执行路径，
 * 不需要 LLM 参与每步决策，显著减少 LLM 调用轮次和 token 消耗。
 * <p>
 * 典型场景：
 * <ul>
 *   <li>搜索 → 汇总 → 生成文档</li>
 *   <li>获取网页 → 解析 → 验证</li>
 *   <li>读取文件 → 分析 → 修改 → 保存</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolPipeline {

    private String pipelineId;

    private String name;

    private String description;

    /**
     * 管道步骤列表，按顺序执行。
     */
    private List<ToolPipelineStep> steps;

    /**
     * 管道级别超时秒数。默认 300。
     */
    @Builder.Default
    private int timeoutSeconds = 300;

    /**
     * 执行上下文 — 由 Orchestrator 注入，传递给 PolicyEngine 用于策略评估。
     * 使用 Map<String, Object> 避免 mcp-tools → mcp-agent-engine 的跨模块依赖。
     */
    @Builder.Default
    private Map<String, Object> executionContext = Map.of();
}