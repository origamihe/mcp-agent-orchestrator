package com.mcp.gateway.handler;

import com.mcp.common.channel.ChannelMessage;
import com.mcp.common.channel.ChannelReply;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 渠道异常处理器 — 将 Agent 执行异常转换为用户友好的降级回复。
 *
 * 分类处理：
 * - TimeoutException: "思考超时，请稍后再试"
 * - RateLimitException (429): "当前请求过多，请稍等片刻"
 * - ConnectionException: "服务暂时不可用，请稍后重试"
 * - 其他: "处理请求时遇到问题，请稍后再试"
 */
@Slf4j
@Component
public class ChannelErrorHandler {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    private static final Map<String, String> ERROR_PATTERNS = Map.ofEntries(
            Map.entry("429", "当前请求过多，请稍等片刻再试。"),
            Map.entry("quota", "当前 API 配额已用尽，请稍后再试。"),
            Map.entry("timed out", "处理超时，请稍候再试。"),
            Map.entry("timeout", "处理超时，请稍候再试。"),
            Map.entry("time out", "处理超时，请稍候再试。"),
            Map.entry("connect", "服务暂时不可用，请稍后重试。"),
            Map.entry("refused", "服务暂时不可用，请稍后重试。"),
            Map.entry("reset", "连接意外中断，请重试。"),
            Map.entry("502", "上游服务异常，请稍后重试。"),
            Map.entry("503", "服务暂时过载，请稍后重试。"),
            Map.entry("504", "上游服务超时，请稍后重试。")
    );

    private static final String GENERIC_FALLBACK = "处理请求时遇到问题，请稍后再试。";

    /**
     * 获取默认超时时间。
     */
    public Duration getDefaultTimeout() {
        return DEFAULT_TIMEOUT;
    }

    /**
     * 根据异常类型生成降级回复。
     */
    public ChannelReply buildFallbackReply(ChannelMessage msg, Throwable error) {
        String errorMessage = error.getMessage() != null ? error.getMessage() : "";
        String gracefulMessage = buildGracefulMessage(errorMessage);

        log.warn("[ChannelErrorHandler] 降级回复: originalError='{}' → fallback='{}'",
                errorMessage, gracefulMessage);

        return ChannelReply.builder()
                .targetId(msg.getChatType() == ChannelMessage.ChatType.GROUP
                        ? msg.getChatId() : msg.getSenderId())
                .content(gracefulMessage)
                .chatType(msg.getChatType())
                .sendAsVoice(false)
                .mentionTargetId(msg.getSenderId())
                .build();
    }

    /**
     * 为群聊消息构建降级回复（带 @mention）。
     */
    public ChannelReply buildFallbackReplyForGroup(ChannelMessage msg, Throwable error) {
        ChannelReply reply = buildFallbackReply(msg, error);
        return reply;
    }

    /**
     * 构建优雅降级消息。
     */
    private String buildGracefulMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return GENERIC_FALLBACK;
        }

        String lower = errorMessage.toLowerCase();

        for (var entry : ERROR_PATTERNS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return GENERIC_FALLBACK;
    }

    /**
     * 判断是否为可恢复的错误（非致命）。
     */
    public boolean isRecoverable(Throwable error) {
        if (error instanceof TimeoutException) return true;
        String msg = error.getMessage() != null ? error.getMessage().toLowerCase() : "";
        return msg.contains("429") || msg.contains("503") || msg.contains("504")
                || msg.contains("timeout") || msg.contains("connect")
                || msg.contains("reset") || msg.contains("refused");
    }
}