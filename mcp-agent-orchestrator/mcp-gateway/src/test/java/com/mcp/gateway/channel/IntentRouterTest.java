package com.mcp.gateway.channel;

import com.mcp.common.channel.IntentType;
import com.mcp.common.channel.RecallMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IntentRouter 意图识别单元测试
 * 重点覆盖 RECALL_HISTORY + RecallMode（USER_ONLY / CONVERSATION / BOTH）的边界场景
 */
class IntentRouterTest {

    private IntentRouter router;

    @BeforeEach
    void setUp() {
        router = new IntentRouter();
    }

    // ==================== 场景1：只回用户消息 ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "列出我说过的话",
            "把我说过的话列出来",
            "逐条列出我说过的内容",
            "还记得我说过什么吗",
            "我说过的全部列出来",
            "复述一下我说的话",
            "列举一下我说过的所有话"
    })
    @DisplayName("「列出我说过的话」→ 只回用户消息（RECALL_HISTORY + USER_ONLY）")
    void shouldDetectRecallMyMessages(String userMessage) {
        IntentRouter.IntentResult result = router.detect("test-session", userMessage);
        assertEquals(IntentType.RECALL_HISTORY, result.intent(),
                "期望 RECALL_HISTORY，实际: " + result.intent() + "，输入: " + userMessage);
        assertEquals(RecallMode.USER_ONLY, result.recallMode(),
                "期望 RecallMode.USER_ONLY，实际: " + result.recallMode());
    }

    // ==================== 场景2：回完整对话 ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "我们之前聊了什么",
            "聊天记录",
            "回顾一下我们的对话",
            "刚才说了什么",
            "总结一下聊天内容",
            "复盘聊天",
            "历史对话",
            "对话历史"
    })
    @DisplayName("「我们之前聊了什么」→ 回完整对话（RECALL_HISTORY + CONVERSATION）")
    void shouldDetectRecallConversation(String userMessage) {
        IntentRouter.IntentResult result = router.detect("test-session", userMessage);
        assertEquals(IntentType.RECALL_HISTORY, result.intent(),
                "期望 RECALL_HISTORY，实际: " + result.intent() + "，输入: " + userMessage);
        assertEquals(RecallMode.CONVERSATION, result.recallMode(),
                "期望 RecallMode.CONVERSATION，实际: " + result.recallMode());
    }

    // ==================== 场景3：双模式同时命中 → 走兜底 AMBIGUOUS ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "把我说过的和聊天记录都列出来",
            "列出我说过的话和全部聊天记录",
            "逐条列出我说过的，还有整个聊天记录",
            "回顾对话，把我说的都列出来"
    })
    @DisplayName("「把我说过的和聊天记录都列出来」→ RECALL_HISTORY + BOTH")
    void shouldDetectBothWhenBothRecallPatternsMatch(String userMessage) {
        IntentRouter.IntentResult result = router.detect("test-session", userMessage);
        assertEquals(IntentType.RECALL_HISTORY, result.intent(),
                "期望 RECALL_HISTORY，实际: " + result.intent() + "，输入: " + userMessage);
        assertEquals(RecallMode.BOTH, result.recallMode(),
                "期望 RecallMode.BOTH，实际: " + result.recallMode());
    }

    // ==================== 场景4：普通聊天不走回顾 ====================

    @ParameterizedTest
    @ValueSource(strings = {
            "你好",
            "今天天气怎么样",
            "帮我写一段代码",
            "讲个笑话"
    })
    @DisplayName("普通聊天 → CHAT，不走回顾")
    void shouldDetectChatForNormalMessage(String userMessage) {
        IntentRouter.IntentResult result = router.detect("test-session", userMessage);
        assertEquals(IntentType.CHAT, result.intent(),
                "期望 CHAT，实际: " + result.intent() + "，输入: " + userMessage);
    }

    // ==================== 场景5：模式切换不被误判 ====================

    @Test
    @DisplayName("「文字模式」→ SWITCH_TEXT_MODE，不被回顾规则误判")
    void shouldDetectTextModeSwitch() {
        IntentRouter.IntentResult result = router.detect("test-session", "文字模式");
        assertEquals(IntentType.SWITCH_TEXT_MODE, result.intent());
    }

    @Test
    @DisplayName("「语音模式」→ SWITCH_VOICE_MODE，不被回顾规则误判")
    void shouldDetectVoiceModeSwitch() {
        IntentRouter.IntentResult result = router.detect("test-session", "语音模式");
        assertEquals(IntentType.SWITCH_VOICE_MODE, result.intent());
    }
}