package com.mcp.gateway.channel;

import com.mcp.common.channel.ChannelMessage;
import com.mcp.common.channel.ChannelReply;
import com.mcp.common.tts.TtsService;
import com.mcp.engine.orchestrator.AgentOrchestrator;
import com.mcp.gateway.ws.WebSocketSessionManager;
import com.mcp.tools.tool.DocxGeneratorTool;
import com.mcp.tools.tool.PptGeneratorTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ChannelOrchestrator {

    private final AgentOrchestrator agentOrchestrator;
    private final ChannelAdapterRegistry adapterRegistry;
    private final TtsService ttsService;
    private final DocxGeneratorTool docxGeneratorTool;
    private final PptGeneratorTool pptGeneratorTool;
    private final WebSocketSessionManager wsSessionManager;

    @Value("${docx.output.dir:./generated/docx}")
    private String docxOutputDir;

    @Value("${ppt.output.dir:./generated/ppt}")
    private String pptOutputDir;

    public ChannelOrchestrator(AgentOrchestrator agentOrchestrator,
                                ChannelAdapterRegistry adapterRegistry,
                                TtsService ttsService,
                                DocxGeneratorTool docxGeneratorTool,
                                PptGeneratorTool pptGeneratorTool,
                                WebSocketSessionManager wsSessionManager) {
        this.agentOrchestrator = agentOrchestrator;
        this.adapterRegistry = adapterRegistry;
        this.ttsService = ttsService;
        this.docxGeneratorTool = docxGeneratorTool;
        this.pptGeneratorTool = pptGeneratorTool;
        this.wsSessionManager = wsSessionManager;
    }

    /**
     * 会话级别的回复模式（每个 session 独立）
     * true = 语音模式（默认，日语 TTS）
     * false = 文字模式（中文文本）
     */
    private final Map<String, Boolean> sessionVoiceMode = new ConcurrentHashMap<>();

    /** 切换到文字模式的触发词 */
    private static final Pattern TEXT_MODE_TRIGGER = Pattern.compile(
            "用文字回复|文字で返信|テキストで|不用语音|不要语音|用中文回复|中文で|テキストモード"
    );

    /** 切换回语音模式的触发词 */
    private static final Pattern VOICE_MODE_TRIGGER = Pattern.compile(
            "用语音回复|语音で返信|ボイスで|用日语回复|日本語で|语音|ボイスモード"
    );

    /**
     * 匹配括号内的心理描写/动作描写，这些内容不应转为语音
     * 例如：（微笑）（笑）（轻轻叹了口气）（微笑，带着一丝好奇）
     */
    private static final Pattern PSYCH_DESCRIPTION_PATTERN = Pattern.compile(
            "[（(][^）)]*[）)]"
    );

    /** 文档生成请求关键词 */
    private static final Pattern DOCX_REQUEST_PATTERN = Pattern.compile(
            "创建文档|生成文档|生成docx|生成word|写个文档|写一个文档|制作文档|" +
            "生成Word|word文档|做文档|创建word|生成一份文档|写文档|创建docx|" +
            "帮我写一份|帮我写个|帮我生成.*文档|帮我生成.*word|帮我生成.*docx",
            Pattern.CASE_INSENSITIVE
    );

    /** PPT生成请求关键词 */
    private static final Pattern PPT_REQUEST_PATTERN = Pattern.compile(
            "创建PPT|生成PPT|制作PPT|做一个PPT|做个PPT|生成ppt|演示文稿|幻灯片|" +
            "做PPT|创建ppt|做.*PPT|创建演示文稿|生成演示文稿|创建幻灯片|" +
            "帮我生成.*PPT|帮我生成.*ppt|帮我生成.*演示|帮我生成.*幻灯片",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 统一的渠道消息处理入口
     * 所有渠道的消息都走这里，与平台无关
     */
    public Mono<Void> handleMessage(String channelType, Object rawPayload) {
        ChannelAdapter adapter = adapterRegistry.get(channelType)
                .orElseThrow(() -> new IllegalArgumentException("Unknown channel: " + channelType));

        if (!adapter.isEnabled()) {
            return Mono.empty();
        }

        // Step 1: 平台消息 → 通用模型
        ChannelMessage msg = adapter.normalize(rawPayload);
        if (msg.getContent() == null || msg.getContent().trim().isEmpty()) {
            return Mono.empty();
        }

        String userMessage = msg.getContent().trim();
        String sessionId = msg.getPlatformSessionId();

        log.info("[Channel:{}] Processing message from {} (chat={}): {}",
                channelType, msg.getSenderId(), msg.getChatId(), userMessage);

        // Step 2: 检测用户是否切换回复模式
        boolean shouldUseVoice = detectAndUpdateVoiceMode(sessionId, userMessage);

        // Step 2.5: 检测是否为文件生成请求
        if (DOCX_REQUEST_PATTERN.matcher(userMessage).find()) {
            return handleDocxGeneration(msg, adapter, userMessage, sessionId, shouldUseVoice);
        }
        if (PPT_REQUEST_PATTERN.matcher(userMessage).find()) {
            return handlePptGeneration(msg, adapter, userMessage, sessionId, shouldUseVoice);
        }

        // Step 3: 构建增强后的消息，告知 Agent 应该用什么语言回复
        String enhancedMessage = buildEnhancedMessage(userMessage, shouldUseVoice);

        // Step 4: 构建系统提示（日语语音模式 / 中文文字模式）
        String systemPrompt = buildSystemPrompt(adapter.getSystemPrompt(), shouldUseVoice);

        log.info("[Channel:{}] Reply mode: {} | Session: {}",
                channelType, shouldUseVoice ? "VOICE(JA)" : "TEXT(ZH)", sessionId);

        // Step 5: 调用 Agent 业务层
        return agentOrchestrator.processRequestWithSystemPrompt(
                        enhancedMessage,
                        sessionId,
                        systemPrompt,
                        null    // modelConfigId 可配置
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(agentResponse -> {
                    if (!shouldUseVoice) {
                        // 文字模式：直接发送中文文本，跳过 TTS
                        log.info("[Channel:{}] Text mode: sending Chinese text", channelType);
                        return Mono.just(ChannelReply.builder()
                                .channelType(channelType)
                                .targetId(getReplyTargetId(msg))
                                .content(agentResponse)
                                .chatType(msg.getChatType())
                                .sendAsVoice(false)
                                .build());
                    }

                    // 语音模式：去除括号心理描写，强制限制句长，再调用 TTS 生成日语语音
                    String cleanResponse = enforceVoiceLengthLimit(
                            stripVoiceImpurities(agentResponse));
                    return ttsService.synthesizeToBytes(cleanResponse, "lingyin")
                            .map(voiceData -> ChannelReply.builder()
                                    .channelType(channelType)
                                    .targetId(getReplyTargetId(msg))
                                    .content(cleanResponse)
                                    .chatType(msg.getChatType())
                                    .sendAsVoice(true)
                                    .voiceData(voiceData)
                                    .build())
                            .onErrorResume(e -> {
                                log.warn("[TTS] Voice synthesis failed, fallback to text: {}", e.getMessage());
                                return Mono.just(ChannelReply.builder()
                                        .channelType(channelType)
                                        .targetId(getReplyTargetId(msg))
                                        .content(cleanResponse)
                                        .chatType(msg.getChatType())
                                        .sendAsVoice(false)
                                        .build());
                            })
                            .defaultIfEmpty(ChannelReply.builder()
                                    .channelType(channelType)
                                    .targetId(getReplyTargetId(msg))
                                    .content(cleanResponse)
                                    .chatType(msg.getChatType())
                                    .sendAsVoice(false)
                                    .build());
                })
                .flatMap(reply -> adapter.sendReply(reply))
                .doOnSuccess(v -> log.info("[Channel:{}] Reply sent", channelType))
                .doOnError(e -> log.error("[Channel:{}] Error: {}", channelType, e.getMessage(), e))
                .then();
    }

    /**
     * 检测用户消息中的模式切换指令，更新会话状态
     * @return 是否应该使用语音模式
     */
    private boolean detectAndUpdateVoiceMode(String sessionId, String userMessage) {
        // 默认：语音模式
        boolean currentMode = sessionVoiceMode.getOrDefault(sessionId, true);

        if (TEXT_MODE_TRIGGER.matcher(userMessage).find()) {
            sessionVoiceMode.put(sessionId, false);
            log.info("[VoiceMode] Session {} → TEXT mode (Chinese)", sessionId);
            return false;
        }

        if (VOICE_MODE_TRIGGER.matcher(userMessage).find()) {
            sessionVoiceMode.put(sessionId, true);
            log.info("[VoiceMode] Session {} → VOICE mode (Japanese)", sessionId);
            return true;
        }

        return currentMode;
    }

    /**
     * 构建增强后的用户消息，加入语言指令
     */
    private String buildEnhancedMessage(String userMessage, boolean isVoiceMode) {
        if (isVoiceMode) {
            // 语音模式：让 Agent 理解中文，但用日语回复
            return userMessage + "\n\n【システム指示：あなたは日本語の音声合成（TTS）で返信します。必ず日本語で回答してください。】";
        } else {
            // 文字模式：中文回复
            return userMessage + "\n\n【系统指示：请用中文文字回复。】";
        }
    }

    /**
     * 语音模式：强制限制句长，防止 TTS 合成失败
     * - 每句不超过 MAX_SENTENCE_LENGTH 字符
     * - 超长句子在顿号或助词处自然分割
     * - 整体不超过 MAX_TOTAL_LENGTH 字符
     */
    private String enforceVoiceLengthLimit(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        final int MAX_SENTENCE_LENGTH = 45;
        final int MAX_TOTAL_LENGTH = 250;

        StringBuilder result = new StringBuilder();
        int totalLen = 0;

        // 按句子分隔符拆分
        String[] sentences = text.split("(?<=[。！？！？])");
        for (String sentence : sentences) {
            if (sentence.isBlank()) continue;
            String trimmed = sentence.trim();

            if (trimmed.length() <= MAX_SENTENCE_LENGTH) {
                if (totalLen + trimmed.length() > MAX_TOTAL_LENGTH) {
                    break;
                }
                result.append(trimmed);
                totalLen += trimmed.length();
            } else {
                // 超长句子：在顿号或助词处自然断开
                String[] parts = splitLongSentence(trimmed, MAX_SENTENCE_LENGTH);
                for (String part : parts) {
                    if (totalLen + part.length() > MAX_TOTAL_LENGTH) {
                        break;
                    }
                    result.append(part);
                    totalLen += part.length();
                }
            }
            if (totalLen >= MAX_TOTAL_LENGTH) break;
        }

        String finalText = result.toString().trim();
        if (!finalText.isEmpty() && !finalText.matches(".*[。！？」）)]$")) {
            finalText += "。";
        }
        return finalText;
    }

    /**
     * 将超长句子在自然断点处分割
     */
    private String[] splitLongSentence(String sentence, int maxLen) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        int start = 0;
        while (start < sentence.length()) {
            if (start + maxLen >= sentence.length()) {
                String part = sentence.substring(start).trim();
                if (!part.isEmpty()) parts.add(ensureEndsWithPunctuation(part));
                break;
            }
            // 在 maxLen 范围内找最自然的断点
            int end = start + maxLen;
            int splitPos = -1;
            for (int i = end; i > start + maxLen / 3; i--) {
                char c = sentence.charAt(i);
                if (c == '、' || c == '，' || c == ' ') {
                    splitPos = i + 1;
                    break;
                }
                // 在助词后断句更自然
                if ((c == 'は' || c == 'が' || c == 'を' || c == 'に' || c == 'で' || c == 'と' || c == 'へ' || c == 'も' || c == 'か' || c == 'ね' || c == 'よ') && i + 1 < sentence.length()) {
                    splitPos = i + 1;
                    break;
                }
            }
            if (splitPos == -1) {
                splitPos = end;
            }
            String part = sentence.substring(start, splitPos).trim();
            if (!part.isEmpty()) parts.add(ensureEndsWithPunctuation(part));
            start = splitPos;
        }
        return parts.toArray(new String[0]);
    }

    private String ensureEndsWithPunctuation(String text) {
        if (text.matches(".*[。！？）」)]$")) return text;
        return text + "。";
    }

    private String stripVoiceImpurities(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return PSYCH_DESCRIPTION_PATTERN.matcher(text).replaceAll("").trim();
    }

    /**
     * 构建最终的系统提示
     */
    private String buildSystemPrompt(String baseSystemPrompt, boolean isVoiceMode) {
        if (isVoiceMode) {
            return baseSystemPrompt + "\n\n"
                    + "【重要：言語ルール】\n"
                    + "あなたはQQボット「澪音」です。ユーザーは中国語で話しかけてきますが、あなたは必ず日本語で返信してください。\n"
                    + "理由：あなたの返信は日本語の音声合成エンジン（TTS）で読み上げられます。中国語のテキストは正しく発音できません。\n"
                    + "日本語で自然に、優しく、親しみやすい会話を心がけてください。\n"
                    + "ユーザーが「用文字回复」「文字で返信」と言った場合は、中国語のテキストで返信してください。\n"
                    + "\n"
                    + "【厳守：TTS音声出力の制約】\n"
                    + "あなたの返信は音声合成エンジンで読み上げられます。以下のルールを必ず守ってください：\n"
                    + "1. 一文は40文字以内に収めてください。それ以上長くなると音声変換に失敗します。\n"
                    + "2. 長い説明が必要な場合は、短い文に分割してください。\n"
                    + "3. 「そして」「しかし」「ですが」「ので」「から」などの接続詞で文をつなげすぎないでください。\n"
                    + "4. 自然な会話のリズムを意識し、一文一意を心がけてください。\n"
                    + "5. 返信全体もコンパクトにまとめ、200文字以内を目安にしてください。\n"
                    + "6. 複数の話題を一度に盛り込まず、最も重要なポイントに絞ってください。\n"
                    + "\n"
                    + "【厳禁】括弧「（）」を使った心理描写・動作描写（例：「（微笑）」「（笑）」「（ため息）」など）は絶対に使わないでください。"
                    + "あなたの返信は音声で読み上げられるため、括弧内の文字もそのまま読み上げられてしまいます。";
        } else {
            return baseSystemPrompt + "\n\n"
                    + "【重要：语言规则】\n"
                    + "当前为文字模式，请用中文回复用户。";
        }
    }

    private String getReplyTargetId(ChannelMessage msg) {
        return msg.getChatType() == ChannelMessage.ChatType.GROUP
                ? msg.getChatId()
                : msg.getSenderId();
    }

    private Mono<Void> handleDocxGeneration(ChannelMessage msg, ChannelAdapter adapter,
                                             String userMessage, String sessionId, boolean shouldUseVoice) {
        String topic = extractTopic(userMessage);
        String docPrompt = """
                你是一位专业的文档编写专家。请根据用户提供的主题和内容描述，生成一份结构清晰的 Word 文档内容。
                
                【严格要求】
                1. 必须以纯JSON格式输出，不要包含任何其他文字、解释或markdown标记
                2. JSON结构必须严格遵循以下格式：
                {"title": "文档主标题", "sections": [{"title": "章节标题", "content": ["段落1内容", "段落2内容", ...]}, ...]}
                3. 第一个章节作为文档开头，后续章节展开详细内容
                4. 每个章节的content数组包含1-5个段落，段落内容详细充实
                5. 总共生成3-6个章节
                6. 内容要专业、有条理，适合正式文档
                7. 只输出JSON，不要输出```json```等标记
                
                文档主题：%s
                """.formatted(topic);

        String systemPrompt = buildSystemPrompt(adapter.getSystemPrompt(), shouldUseVoice);

        return agentOrchestrator.processRequestWithSystemPrompt(docPrompt, sessionId, systemPrompt, null)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(llmResponse -> {
                    try {
                        DocxGeneratorTool.DocxResult result = docxGeneratorTool.generateDocx(llmResponse, topic);
                        log.info("[Channel:qq] DOCX generated: {}", result.downloadUrl());
                        wsSessionManager.broadcast(result.downloadUrl());

                        Path filePath = Path.of(docxOutputDir, result.fileName()).toAbsolutePath().normalize();
                        ChannelReply fileReply = ChannelReply.builder()
                                .channelType("qq")
                                .targetId(getReplyTargetId(msg))
                                .chatType(msg.getChatType())
                                .sendAsFile(true)
                                .filePath(filePath.toString())
                                .fileUrl(result.downloadUrl())
                                .content(result.message())
                                .build();

                        return adapter.sendReply(fileReply).thenReturn(result.message());
                    } catch (Exception e) {
                        log.error("[Channel:qq] DOCX generation failed: {}", e.getMessage(), e);
                        return Mono.just("文档生成失败，请稍后重试。");
                    }
                })
                .flatMap(textMsg -> {
                    ChannelReply textReply = ChannelReply.builder()
                            .channelType("qq")
                            .targetId(getReplyTargetId(msg))
                            .content(textMsg)
                            .chatType(msg.getChatType())
                            .sendAsVoice(false)
                            .build();
                    return adapter.sendReply(textReply);
                })
                .doOnSuccess(v -> log.info("[Channel:qq] DOCX reply sent"))
                .doOnError(e -> log.error("[Channel:qq] DOCX error: {}", e.getMessage(), e))
                .then();
    }

    private Mono<Void> handlePptGeneration(ChannelMessage msg, ChannelAdapter adapter,
                                            String userMessage, String sessionId, boolean shouldUseVoice) {
        String topic = extractTopic(userMessage);
        String pptPrompt = """
                你是一位专业的演示文稿设计专家。请根据用户提供的主题和内容描述，生成一份结构清晰的PPT内容。
                
                【严格要求】
                1. 必须以纯JSON格式输出，不要包含任何其他文字、解释或markdown标记
                2. JSON结构必须严格遵循以下格式：
                {"title": "PPT主标题", "slides": [{"title": "页面标题", "content": ["要点1", "要点2", "要点3"]}, ...]}
                3. 第一个slide作为封面（包含主标题和副标题），后续slides展开详细内容
                4. 每页slides的content数组包含3-5个要点，每个要点的文字简洁有力
                5. 总共生成5-8页slides
                6. 内容要专业、有条理，适合演讲展示
                7. 只输出JSON，不要输出```json```等标记
                
                PPT主题：%s
                """.formatted(topic);

        String systemPrompt = buildSystemPrompt(adapter.getSystemPrompt(), shouldUseVoice);

        return agentOrchestrator.processRequestWithSystemPrompt(pptPrompt, sessionId, systemPrompt, null)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(llmResponse -> {
                    try {
                        PptGeneratorTool.PptResult result = pptGeneratorTool.generatePptx(llmResponse, topic);
                        log.info("[Channel:qq] PPT generated: {}", result.downloadUrl());
                        wsSessionManager.broadcast(result.downloadUrl());

                        Path filePath = Path.of(pptOutputDir, result.fileName()).toAbsolutePath().normalize();
                        ChannelReply fileReply = ChannelReply.builder()
                                .channelType("qq")
                                .targetId(getReplyTargetId(msg))
                                .chatType(msg.getChatType())
                                .sendAsFile(true)
                                .filePath(filePath.toString())
                                .fileUrl(result.downloadUrl())
                                .content(result.message())
                                .build();

                        return adapter.sendReply(fileReply).thenReturn(result.message());
                    } catch (Exception e) {
                        log.error("[Channel:qq] PPT generation failed: {}", e.getMessage(), e);
                        return Mono.just("PPT生成失败，请稍后重试。");
                    }
                })
                .flatMap(textMsg -> {
                    ChannelReply textReply = ChannelReply.builder()
                            .channelType("qq")
                            .targetId(getReplyTargetId(msg))
                            .content(textMsg)
                            .chatType(msg.getChatType())
                            .sendAsVoice(false)
                            .build();
                    return adapter.sendReply(textReply);
                })
                .doOnSuccess(v -> log.info("[Channel:qq] PPT reply sent"))
                .doOnError(e -> log.error("[Channel:qq] PPT error: {}", e.getMessage(), e))
                .then();
    }

    private String extractTopic(String userMessage) {
        String topic = userMessage
                .replaceAll("(?i)帮我生成|帮我|请帮我|给我|创建一个|生成一个|制作一个|写一个|做一个|弄一个", "")
                .replaceAll("(?i)文档|word|docx|PPT|ppt|演示文稿|幻灯片", "")
                .replaceAll("的$", "")
                .trim();
        return topic.isEmpty() ? "未命名" : topic;
    }
}