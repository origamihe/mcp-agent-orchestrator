package com.mcp.core.context.provider;

import com.mcp.core.context.BuildContext;
import com.mcp.core.context.ContextProvider;
import com.mcp.core.context.PromptContext;
import org.springframework.stereotype.Component;

/**
 * Host 上下文提供者 — 填充 Host 环境感知层（hostContext）。
 * 注入当前 Host 感知到的环境状态（当前文件、Git Diff、剪贴板等）。
 */
@Component
public class HostContextProvider implements ContextProvider {

    @Override
    public void collect(PromptContext.PromptContextBuilder builder, BuildContext ctx) {
        String hostContextPrompt = ctx.hostContextPrompt();
        if (hostContextPrompt != null && !hostContextPrompt.isEmpty()) {
            builder.hostContext(hostContextPrompt);
        }
    }
}