package com.mcp.gateway.channel;

import com.mcp.common.channel.AgentMode;
import com.mcp.common.channel.IntentType;
import com.mcp.common.channel.RecallMode;
import com.mcp.common.channel.RoleRuntime;
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

    private static final Pattern GAME_MODE_ON_TRIGGER = Pattern.compile(
            "开始跑团|进入跑团|跑团模式|开始角色扮演|进入角色|trpg|COC跑团|克苏鲁跑团|TRPG模式",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern GAME_MODE_OFF_TRIGGER = Pattern.compile(
            "结束跑团|退出跑团|停止跑团|退出角色|结束角色扮演|关闭跑团|关掉跑团",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * DOCX 意图快速预检关键词。
     * 在完整的 DOCX_KEYWORDS 正则匹配之前先做轻量级检查，
     * 过滤掉绝大多数不相关的消息，避免每次请求都运行 60+ 分支的正则。
     */
    private static final Pattern DOCX_QUICK_CHECK = Pattern.compile(
            "文档|word|docx|文件|生成|创建|制作|写|整理|汇总|总结|输出|导出|保存|发送",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DOCX_KEYWORDS = Pattern.compile(
            "创建文档|生成文档|生成docx|生成word|写个文档|写一个文档|制作文档|" +
                    "生成Word|word文档|做文档|创建word|生成一份文档|写文档|创建docx|" +
                    "帮我写一份|帮我写个|帮我生成.*文档|帮我生成.*word|帮我生成.*docx|" +
                    "整理成文档|整理成word|整理成docx|整理成文件|整理.*文件|" +
                    "输出为文档|输出为word|输出为docx|输出为文件|输出.*文件|" +
                    "导出.*文档|导出.*word|导出.*docx|导出.*文件|" +
                    "保存.*文档|保存.*word|保存.*docx|保存.*文件|" +
                    "生成.*文件|创建.*文件|写.*文件|汇总.*文件|总结.*文件|" +
                    "搜索.*文档|搜.*文档|查.*文档|找.*文档|" +
                    "搜索.*文件|搜.*文件|查.*文件|找.*文件|" +
                    "搜索.*生成.*文档|搜索.*生成.*word|搜索.*生成.*docx|" +
                    "搜索.*生成.*文件|" +
                    "搜索.*整理.*文档|搜索.*整理.*word|搜索.*整理.*docx|" +
                    "搜索.*整理.*文件|" +
                    "搜索.*写.*文档|搜索.*写.*word|搜索.*写.*docx|" +
                    "搜索.*写.*文件|" +
                    "搜.*并.*生成.*文档|搜.*并.*生成.*word|搜.*并.*生成.*docx|" +
                    "搜.*并.*生成.*文件|" +
                    "搜.*然后.*生成.*文档|搜.*然后.*生成.*word|搜.*然后.*生成.*docx|" +
                    "搜.*然后.*生成.*文件|" +
                    "查.*并.*生成.*文档|查.*并.*生成.*word|查.*并.*生成.*docx|" +
                    "查.*并.*生成.*文件|" +
                    "查.*然后.*生成.*文档|查.*然后.*生成.*word|查.*然后.*生成.*docx|" +
                    "查.*然后.*生成.*文件|" +
                    "找.*并.*生成.*文档|找.*并.*生成.*word|找.*并.*生成.*docx|" +
                    "找.*并.*生成.*文件|" +
                    "找.*然后.*生成.*文档|找.*然后.*生成.*word|找.*然后.*生成.*docx|" +
                    "找.*然后.*生成.*文件|" +
                    "帮我搜.*文档|帮我搜.*word|帮我搜.*docx|帮我搜.*文件|" +
                    "帮我查.*文档|帮我查.*word|帮我查.*docx|帮我查.*文件|" +
                    "帮我找.*文档|帮我找.*word|帮我找.*docx|帮我找.*文件|" +
                    "查一下.*文档|查一下.*word|查一下.*docx|查一下.*文件|" +
                    "搜一下.*文档|搜一下.*word|搜一下.*docx|搜一下.*文件|" +
                    "整理.*文档|整理.*word|整理.*docx|整理.*文件|" +
                    "汇总.*文档|汇总.*word|汇总.*docx|汇总.*文件|" +
                    "总结.*文档|总结.*word|总结.*docx|总结.*文件",
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

    private static final Pattern SEARCH_KEYWORDS = Pattern.compile(
            "搜索|搜索一下|搜一下|帮我搜|帮我搜索|帮我查|查一下|查查|"
                    + "查找|检索|查询|搜寻|搜集|调研|"
                    + "最新.*新闻|最新.*消息|最新.*动态|最新.*资讯|"
                    + "今天.*新闻|今天.*热点|实时|"
                    + "网上查|网上搜|联网搜|联网查|"
                    + "帮我找|找一下|找找",
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
        if (GAME_MODE_ON_TRIGGER.matcher(userMessage).find()) {
            state.setMode(AgentMode.GAME);
            state.setRoleRuntime(RoleRuntime.fromMode(AgentMode.GAME));
            state.touch();
            log.info("[IntentRouter] Session {} → GAME MODE ON", sessionId);
            return new IntentResult(IntentType.SWITCH_GAME_MODE_ON, null, state, null);
        }
        if (GAME_MODE_OFF_TRIGGER.matcher(userMessage).find()) {
            state.setMode(AgentMode.CHAT);
            state.setRoleRuntime(null);
            state.touch();
            log.info("[IntentRouter] Session {} → GAME MODE OFF", sessionId);
            return new IntentResult(IntentType.SWITCH_GAME_MODE_OFF, null, state, null);
        }

        // 2. 文件生成检测（两层：快速预检 → 完整正则匹配）
        boolean isDocx = isDocxCandidate(userMessage) && DOCX_KEYWORDS.matcher(userMessage).find();
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

        // 4. 搜索意图检测（纯搜索请求，不含文档/PPT生成意图）
        // 仅当不匹配 DOCX/PPT 时才视为纯搜索，避免与生成任务冲突
        if (SEARCH_KEYWORDS.matcher(userMessage).find()) {
            log.info("[IntentRouter] Session {} → SEARCH: {}", sessionId, userMessage);
            state.touch();
            return new IntentResult(IntentType.SEARCH, null, state, null);
        }

        // 5. 默认：普通聊天
        state.touch();
        return new IntentResult(IntentType.CHAT, null, state, null);
    }

    /**
     * DOCX 候选快速预检：仅匹配核心关键词，避免对不相关消息运行完整正则。
     */
    private boolean isDocxCandidate(String message) {
        return DOCX_QUICK_CHECK.matcher(message).find();
    }

    String extractTopic(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return "未指定主题";

        // P2 改进：保留结构化指令词（如"整理成报告"），仅移除纯格式词和冗余前缀
        // 策略：分两步清洗
        // Step 1: 移除冗余前缀（帮我/请帮我/给我等）和纯文件格式词（docx/word/ppt/演示文稿等）
        // Step 2: 保留"整理成"、"汇总"、"总结"等结构化意图词，它们传达了用户对输出格式的期望
        String cleaned = userMessage
                // 移除纯请求前缀（不携带语义信息）
                .replaceAll("(?i)请帮我|帮我生成|帮我|给我|创建一个|生成一个|制作一个|写一个|做一个|弄一个|请", "")
                // 移除纯文件格式标识（不影响内容语义）
                .replaceAll("(?i)文档|word|docx|PPT|ppt|演示文稿|幻灯片|" +
                        "输出为|导出为|保存为|发送|输出|导出|保存", "")
                // 移除连接词（保留"然后"以维持顺序语义）
                .replaceAll("(?i)并且|并|再|接着|之后|最后|发我|发给我", "，")
                // 归一化标点
                .replaceAll("[,，。；;\\s]+", "，")
                .replaceAll("，+", "，")
                .replaceAll("^[，,]+|[，,]+$", "")
                .trim();

        if (cleaned.isEmpty() || cleaned.length() < 2) {
            log.warn("[IntentRouter] extractTopic: cleaned result too short ({}), using original: {}",
                    cleaned.length(), userMessage);
            return userMessage.length() > 100 ? userMessage.substring(0, 100) : userMessage;
        }

        return cleaned;
    }

    public record IntentResult(IntentType intent, GenerationTask task, SessionState state, RecallMode recallMode) {}
}