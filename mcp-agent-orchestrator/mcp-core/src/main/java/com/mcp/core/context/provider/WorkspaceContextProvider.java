package com.mcp.core.context.provider;

import com.mcp.core.context.BuildContext;
import com.mcp.core.context.ContextProvider;
import com.mcp.core.context.PromptContext;
import org.springframework.stereotype.Component;

/**
 * 工作空间上下文提供者 — 填充工作空间层（workspace）。
 * 一次性注入 Agent 的长期工作状态，跨会话、跨 Host 持久化。
 */
@Component
public class WorkspaceContextProvider implements ContextProvider {

    @Override
    public void collect(PromptContext.PromptContextBuilder builder, BuildContext ctx) {
        String workspacePrompt = ctx.workspacePrompt();
        if (workspacePrompt != null && !workspacePrompt.isEmpty()) {
            builder.workspace(workspacePrompt);
        }
    }
}