package com.mcp.gateway.channel;

import com.mcp.common.channel.IntentType;
import com.mcp.common.channel.RecallMode;
import com.mcp.common.channel.SessionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Component
public class IntentRouter {

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    private static final Pattern TEXT_MODE_TRIGGER = Pattern.compile(
            "文字模式|用文字回复|不用语音|不要语音|不要发语音|别发语音|用中文回复|关语音|关闭语音|关掉语音"
    );

    private static final Pattern VOICE_MODE_TRIGGER = Pattern.compile(
            "语音模式|用语音回复|用中文语音|开语音|开启语音|打开语音"
    );

    private static final Pattern DOCX_KEYWORDS = Pattern.compile(
            "创建文档|生成文档|生成docx|生成word|写个文档|写一个文档|制作文档|" +
                    "生成Word|word文档|做文档|创建word|生成一份文档|写文档|创建docx|" +
                    "帮我写一份|帮我写个|帮我生成.*文档|帮我生成.*word|帮我生成.*docx",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PPT_KEYWORDS = Pattern.compile(
            "创建PPT|生成PPT|制作PPT|做一个PPT|做个PPT|生成ppt|演示文稿|幻灯片|" +
                    "做PPT|创建ppt|做.*PPT|创建演示文稿|生成演示文稿|创建幻灯片|" +
                    "帮我生成.*PPT|帮我生成.*ppt|帮我生成.*演示|帮我生成.*幻灯片",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern RECALL_MY_MESSAGES_PATTERN = Pattern.compile(
            "列出.*我说|我说过.*话|逐条.*列出|我说.*全部|" +
                    "还记得.*我说|把.*我说.*列出来|列举.*我说|" +
                    "复述.*我说|我.*说过.*什么",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern RECALL_CONVERSATION_PATTERN = Pattern.compile(
            "复述聊天|聊天记录|回顾.*对话|回顾.*聊天|" +
                    "聊天.*全部|历史.*对话|对话.*历史|" +
                    "刚才.*说了.*什么|之前.*聊了.*什么|我们.*聊了.*什么|" +
                    "总结.*聊天|总结.*对话|复盘.*聊天|列举.*聊天|" +
                    "把.*聊天.*列出来",
            Pattern.CASE_INSENSITIVE
    );

    public SessionState getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, k -> new SessionState());
    }

    public IntentResult detect(String sessionId, String userMessage) {
        SessionState state = getOrCreateSession(sessionId);

        // 1. 模式切换检测
        if (TEXT_MODE_TRIGGER.matcher(userMessage).find()) {
            state.setVoiceMode(false);
            state.setLanguage("zh");
            state.touch();
            log.info("[IntentRouter] Session {} → TEXT mode", sessionId);
            return new IntentResult(IntentType.SWITCH_TEXT_MODE, null, state, null);
        }
        if (VOICE_MODE_TRIGGER.matcher(userMessage).find()) {
            state.setVoiceMode(true);
            state.setLanguage("zh");
            state.touch();
            log.info("[IntentRouter] Session {} → VOICE mode", sessionId);
            return new IntentResult(IntentType.SWITCH_VOICE_MODE, null, state, null);
        }

        // 2. 文件生成检测（三层：粗分类 → 歧义处理 → 任务补全）
        boolean isDocx = DOCX_KEYWORDS.matcher(userMessage).find();
        boolean isPpt = PPT_KEYWORDS.matcher(userMessage).find();

        if (isDocx && isPpt) {
            // 歧义：同时包含文档和PPT关键词 → 标记为 AMBIGUOUS，让上层处理澄清
            String topic = extractTopic(userMessage);
            log.info("[IntentRouter] Session {} → AMBIGUOUS (docx+ppt): {}", sessionId, topic);
            GenerationTask task = GenerationTask.of(IntentType.AMBIGUOUS, topic);
            state.touch();
            return new IntentResult(IntentType.AMBIGUOUS, task, state, null);
        }

        if (isDocx) {
            String topic = extractTopic(userMessage);
            log.info("[IntentRouter] Session {} → GENERATE_DOCX: {}", sessionId, topic);
            GenerationTask task = GenerationTask.of(IntentType.GENERATE_DOCX, topic);
            state.touch();
            return new IntentResult(IntentType.GENERATE_DOCX, task, state, null);
        }

        if (isPpt) {
            String topic = extractTopic(userMessage);
            log.info("[IntentRouter] Session {} → GENERATE_PPT: {}", sessionId, topic);
            GenerationTask task = GenerationTask.of(IntentType.GENERATE_PPT, topic);
            state.touch();
            return new IntentResult(IntentType.GENERATE_PPT, task, state, null);
        }

        // 3. 聊天历史回顾检测（按 RecallMode 细分：USER_ONLY / CONVERSATION / BOTH）
        boolean isRecallMy = RECALL_MY_MESSAGES_PATTERN.matcher(userMessage).find();
        boolean isRecallConv = RECALL_CONVERSATION_PATTERN.matcher(userMessage).find();

        if (isRecallMy && isRecallConv) {
            log.info("[IntentRouter] Session {} → RECALL_HISTORY (BOTH): {}", sessionId, userMessage);
            state.touch();
            return new IntentResult(IntentType.RECALL_HISTORY, null, state, RecallMode.BOTH);
        }
        if (isRecallMy) {
            log.info("[IntentRouter] Session {} → RECALL_HISTORY (USER_ONLY): {}", sessionId, userMessage);
            state.touch();
            return new IntentResult(IntentType.RECALL_HISTORY, null, state, RecallMode.USER_ONLY);
        }
        if (isRecallConv) {
            log.info("[IntentRouter] Session {} → RECALL_HISTORY (CONVERSATION): {}", sessionId, userMessage);
            state.touch();
            return new IntentResult(IntentType.RECALL_HISTORY, null, state, RecallMode.CONVERSATION);
        }

        // 4. 默认：普通聊天
        state.touch();
        return new IntentResult(IntentType.CHAT, null, state, null);
    }

    String extractTopic(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return "未指定主题";

        String cleaned = userMessage
                .replaceAll("(?i)帮我生成|帮我|请帮我|给我|创建一个|生成一个|制作一个|写一个|做一个|弄一个", "")
                .replaceAll("(?i)文档|word|docx|PPT|ppt|演示文稿|幻灯片", "")
                .trim();

        return cleaned.isBlank() ? "未指定主题" : cleaned;
    }

    public record IntentResult(IntentType intent, GenerationTask task, SessionState state, RecallMode recallMode) {}
}