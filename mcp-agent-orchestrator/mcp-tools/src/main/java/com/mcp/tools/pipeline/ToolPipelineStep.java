package com.mcp.tools.pipeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具管道中的单个步骤定义。
 * <p>
 * 每个步骤包含工具名称、参数映射和可选的输出提取规则。
 * 参数值支持 ${stepName.fieldName} 语法引用前序步骤的输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolPipelineStep {

    private String stepId;

    private String toolName;

    /**
     * 参数映射。值支持两种形式：
     * <ul>
     *   <li>字面量：直接作为工具参数值</li>
     *   <li>引用：${stepId} 或 ${stepId.fieldName} 引用前序步骤的输出</li>
     * </ul>
     */
    private Map<String, Object> arguments;

    /**
     * 从步骤输出中提取的字段名列表。
     * 为空时保留完整输出。用于后续步骤的 ${stepId.fieldName} 引用。
     */
    private String extractField;

    /**
     * 步骤失败时是否终止管道。默认 true。
     */
    @Builder.Default
    private boolean failFast = true;

    /**
     * 步骤超时秒数。默认 60。
     */
    @Builder.Default
    private int timeoutSeconds = 60;
}