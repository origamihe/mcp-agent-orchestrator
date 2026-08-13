package com.mcp.core.context.provider;

import com.mcp.core.context.BuildContext;
import com.mcp.core.context.ContextProvider;
import com.mcp.core.context.PromptContext;
import org.springframework.stereotype.Component;

/**
 * 群聊对话上下文提供者 — 读取 BuildContext.groupConversationContext，填充到 PromptContext.groupConversation。
 *
 * 由 GroupConversationContextAssembler 在 Orchestrator 层组装好文本后，
 * 通过 BuildContext 传递，本 Provider 仅做"搬运"工作，不负责数据获取。
 */
@Component
public class GroupConversationContextProvider implements ContextProvider {

    @Override
    public void collect(PromptContext.PromptContextBuilder builder, BuildContext ctx) {
        String convCtx = ctx.groupConversationContext();
        if (convCtx != null && !convCtx.isEmpty()) {
            builder.groupConversation(convCtx);
        }
    }
}