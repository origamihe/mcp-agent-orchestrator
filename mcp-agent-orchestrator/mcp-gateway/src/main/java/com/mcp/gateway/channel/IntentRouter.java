package com.mcp.gateway.channel;

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
            "文字模式|テキストモード|用文字回复|文字で返信|テキストで|不用语音|不要语音|不要发语音|别发语音|用中文回复|中文で|关语音|关闭语音|关掉语音"
    );

    private static final Pattern VOICE_MODE_TRIGGER = Pattern.compile(
            "语音模式|ボイスモード|用语音回复|语音で返信|ボイスで|用日语回复|日本語で|开语音|开启语音|打开语音"
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
            return new IntentResult(IntentType.SWITCH_TEXT_MODE, null, state);
        }
        if (VOICE_MODE_TRIGGER.matcher(userMessage).find()) {
            state.setVoiceMode(true);
            state.setLanguage("ja");
            state.touch();
            log.info("[IntentRouter] Session {} → VOICE mode", sessionId);
            return new IntentResult(IntentType.SWITCH_VOICE_MODE, null, state);
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
            return new IntentResult(IntentType.AMBIGUOUS, task, state);
        }

        if (isDocx) {
            String topic = extractTopic(userMessage);
            log.info("[IntentRouter] Session {} → GENERATE_DOCX: {}", sessionId, topic);
            GenerationTask task = GenerationTask.of(IntentType.GENERATE_DOCX, topic);
            state.touch();
            return new IntentResult(IntentType.GENERATE_DOCX, task, state);
        }

        if (isPpt) {
            String topic = extractTopic(userMessage);
            log.info("[IntentRouter] Session {} → GENERATE_PPT: {}", sessionId, topic);
            GenerationTask task = GenerationTask.of(IntentType.GENERATE_PPT, topic);
            state.touch();
            return new IntentResult(IntentType.GENERATE_PPT, task, state);
        }

        // 3. 默认：普通聊天
        state.touch();
        return new IntentResult(IntentType.CHAT, null, state);
    }

    String extractTopic(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return "未指定主题";

        String cleaned = userMessage
                .replaceAll("(?i)帮我生成|帮我|请帮我|给我|创建一个|生成一个|制作一个|写一个|做一个|弄一个", "")
                .replaceAll("(?i)文档|word|docx|PPT|ppt|演示文稿|幻灯片", "")
                .trim();

        return cleaned.isBlank() ? "未指定主题" : cleaned;
    }

    public record IntentResult(IntentType intent, GenerationTask task, SessionState state) {}
}