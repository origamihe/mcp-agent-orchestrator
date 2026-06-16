package com.mcp.gateway.channel;

import com.mcp.common.channel.ChannelMessage;
import com.mcp.common.channel.ChannelReply;
import com.mcp.common.channel.SessionState;
import com.mcp.common.tts.TtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResponsePipeline {

    private final TtsService ttsService;

    private static final Pattern PSYCH_DESCRIPTION_PATTERN = Pattern.compile(
            "[（(][^）)]*[）)]"
    );

    public String getReplyTargetId(ChannelMessage msg) {
        return msg.getChatType() == ChannelMessage.ChatType.GROUP
                ? msg.getChatId()
                : msg.getSenderId();
    }

    public Mono<ChannelReply> process(String channelType, ChannelMessage msg,
                                      String agentResponse, SessionState state) {
        if (!state.isVoiceMode()) {
            return Mono.just(ChannelReply.builder()
                    .channelType(channelType)
                    .targetId(getReplyTargetId(msg))
                    .content(agentResponse)
                    .chatType(msg.getChatType())
                    .sendAsVoice(false)
                    .build());
        }

        // 语音模式 Pipeline: sanitize → segment → synthesize
        String cleanResponse = sanitizeForVoice(agentResponse);
        cleanResponse = segmentForVoice(cleanResponse);

        return synthesizeVoice(cleanResponse, channelType, msg);
    }

    String sanitizeForVoice(String text) {
        if (text == null || text.isBlank()) return text;
        return PSYCH_DESCRIPTION_PATTERN.matcher(text).replaceAll("").trim();
    }

    String segmentForVoice(String text) {
        if (text == null || text.isBlank()) return text;

        final int MAX_SENTENCE_LENGTH = 45;
        final int MAX_TOTAL_LENGTH = 250;

        StringBuilder result = new StringBuilder();
        int totalLen = 0;

        String[] sentences = text.split("(?<=[。！？！？])");
        for (String sentence : sentences) {
            if (sentence.isBlank()) continue;
            String trimmed = sentence.trim();

            if (trimmed.length() <= MAX_SENTENCE_LENGTH) {
                if (totalLen + trimmed.length() > MAX_TOTAL_LENGTH) break;
                result.append(trimmed);
                totalLen += trimmed.length();
            } else {
                String[] parts = splitLongSentence(trimmed, MAX_SENTENCE_LENGTH);
                for (String part : parts) {
                    if (totalLen + part.length() > MAX_TOTAL_LENGTH) break;
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

    private String[] splitLongSentence(String sentence, int maxLen) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        int start = 0;
        while (start < sentence.length()) {
            if (start + maxLen >= sentence.length()) {
                String part = sentence.substring(start).trim();
                if (!part.isEmpty()) parts.add(ensureEndsWithPunctuation(part));
                break;
            }
            int end = start + maxLen;
            int splitPos = -1;
            for (int i = end; i > start + maxLen / 3; i--) {
                char c = sentence.charAt(i);
                if (c == '、' || c == '，' || c == ' ') {
                    splitPos = i + 1;
                    break;
                }
                if ((c == 'は' || c == 'が' || c == 'を' || c == 'に' || c == 'で' || c == 'と' || c == 'へ' || c == 'も' || c == 'か' || c == 'ね' || c == 'よ') && i + 1 < sentence.length()) {
                    splitPos = i + 1;
                    break;
                }
            }
            if (splitPos == -1) splitPos = end;
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

    private Mono<ChannelReply> synthesizeVoice(String cleanText, String channelType, ChannelMessage msg) {
        return ttsService.synthesizeToBytes(cleanText, "lingyin")
                .map(voiceData -> ChannelReply.builder()
                        .channelType(channelType)
                        .targetId(getReplyTargetId(msg))
                        .content(cleanText)
                        .chatType(msg.getChatType())
                        .sendAsVoice(true)
                        .voiceData(voiceData)
                        .build())
                .onErrorResume(e -> {
                    log.warn("[TTS] Voice synthesis failed, fallback to text: {}", e.getMessage());
                    return Mono.just(ChannelReply.builder()
                            .channelType(channelType)
                            .targetId(getReplyTargetId(msg))
                            .content(cleanText)
                            .chatType(msg.getChatType())
                            .sendAsVoice(false)
                            .build());
                })
                .defaultIfEmpty(ChannelReply.builder()
                        .channelType(channelType)
                        .targetId(getReplyTargetId(msg))
                        .content(cleanText)
                        .chatType(msg.getChatType())
                        .sendAsVoice(false)
                        .build());
    }
}