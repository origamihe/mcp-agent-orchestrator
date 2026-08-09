package com.mcp.core.context.provider;

import com.mcp.core.context.BuildContext;
import com.mcp.core.context.ContextProvider;
import com.mcp.core.context.PromptContext;
import org.springframework.stereotype.Component;

/**
 * 世界状态上下文提供者 — 填充世界层（worldState）。
 *
 * 仅在角色/游戏模式下生效。
 */
@Component
public class WorldContextProvider implements ContextProvider {

    @Override
    public void collect(PromptContext.PromptContextBuilder builder, BuildContext ctx) {
        var state = ctx.state();
        if (state == null) {
            return;
        }
        if (state.getMode().isRoleMode() && state.getWorldState() != null) {
            String worldPrompt = state.getWorldState().buildWorldPrompt();
            if (!worldPrompt.isEmpty()) {
                builder.worldState(worldPrompt);
            }
        }
    }
}