package com.mcp.core.context.provider;

import com.mcp.common.channel.SessionState;
import com.mcp.core.context.BuildContext;
import com.mcp.core.context.ContextProvider;
import com.mcp.core.context.PromptContext;
import org.springframework.stereotype.Component;

/**
 * 模式上下文提供者 — 填充模式锁定层（modeLock）和模式提示层（modeHint）。
 */
@Component
public class ModeContextProvider implements ContextProvider {

    @Override
    public void collect(PromptContext.PromptContextBuilder builder, BuildContext ctx) {
        SessionState state = ctx.state();
        if (state == null) {
            return;
        }

        // MODE LOCK 层
        if (state.getMode().isNonChat() && state.getRoleRuntime() != null) {
            builder.modeLock(state.getRoleRuntime().buildRoleLockPrompt());
        }

        // MODE HINT 层
        builder.modeHint(buildModeHint(state));
    }

    private String buildModeHint(SessionState state) {
        if (state.isVoiceMode()) {
            return """
                    【语音模式】
                    当前为语音模式，回复将通过TTS朗读。
                    【TTS约束】
                    1. 每句话控制在40字以内
                    2. 需要长说明时，拆分成短句
                    3. 不要用连词把句子接得太长
                    4. 保持自然对话节奏，一句话一个意思
                    5. 整体回复尽量精简，250字以内
                    6. 不要一次塞太多话题
                    7. 不要使用括号进行心理描写或动作描写，因为语音朗读时会把括号内的文字也读出来
                    """;
        } else {
            return "【语言规则】\n当前为文字模式，请用中文回复用户。";
        }
    }
}