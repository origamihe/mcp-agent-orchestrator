package com.mcp.engine.context;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ContextRequest {

    private String sessionId;
    private String userId;
    private List<String> filePaths;
    private String userRequest;
    /**
     * @deprecated 由 {@link ContextBudget} 统一管理，不再单独设置。
     *             上下文预算通过 {@link #contextBudget} 字段传递。
     */
    @Deprecated
    private int maxFileTokens;
    /**
     * @deprecated 由 {@link ContextBudget} 统一管理，不再单独设置。
     *             上下文预算通过 {@link #contextBudget} 字段传递。
     */
    @Deprecated
    private int maxMemoryTokens;
    /**
     * @deprecated 由 {@link ContextBudget} 统一管理，不再单独设置。
     *             上下文预算通过 {@link #contextBudget} 字段传递。
     */
    @Deprecated
    private int maxHistoryTokens;
    /**
     * P1 上下文预算，替代硬编码常量。
     * 若提供，则 maxFileTokens/maxMemoryTokens/maxHistoryTokens 从 ContextBudget 派生。
     */
    private ContextBudget contextBudget;
}