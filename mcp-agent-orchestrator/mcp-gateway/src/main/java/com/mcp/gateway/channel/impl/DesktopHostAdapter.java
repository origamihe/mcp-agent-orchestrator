package com.mcp.gateway.channel.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcp.common.channel.ChannelMessage;
import com.mcp.common.channel.ChannelReply;
import com.mcp.common.channel.HostContext;
import com.mcp.gateway.channel.ChannelAdapter;
import com.mcp.gateway.ws.WebSocketSessionManager;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Desktop Host 适配器 — Agent 的桌面感知入口。
 * 提供：剪贴板、当前窗口、截图、OCR、文件拖拽、全局快捷键触发。
 * 通过 WebSocket 保持长连接，支持双向通信（消息 + 通知 + 文件操作）。
 */
@Slf4j
@Setter
@Component
@ConfigurationProperties(prefix = "channel.desktop")
public class DesktopHostAdapter implements ChannelAdapter {

    private boolean enabled = true;
    private String systemPrompt = "你是一个桌面助手，可以感知用户的操作环境。";

    private final WebSocketSessionManager wsSessionManager;

    public DesktopHostAdapter(WebSocketSessionManager wsSessionManager) {
        this.wsSessionManager = wsSessionManager;
    }

    @Override
    public String getChannelType() {
        return "desktop";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void start() {
        log.info("[DesktopHost] Started - Desktop Host Adapter is ready");
    }

    @Override
    public void stop() {
        log.info("[DesktopHost] Stopped");
    }

    @Override
    public String getSystemPrompt() {
        return systemPrompt;
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("channel", "desktop");
        status.put("enabled", enabled);
        status.put("connected", true);
        return status;
    }

    /**
     * 将 Desktop Host 发来的 JSON 消息转换为通用 ChannelMessage。
     * 消息格式：
     * {
     *   "type": "message",
     *   "content": "帮我总结剪贴板内容",
     *   "hostContext": {
     *     "clipboardContent": "...",
     *     "activeWindowTitle": "Visual Studio Code",
     *     "activeWindowProcess": "Code.exe",
     *     "selectedFilePaths": ["C:\\Users\\...\\file.txt"],
     *     "screenshotBase64": "...",
     *     "ocrText": "..."
     *   }
     * }
     */
    @Override
    public ChannelMessage normalize(Object rawPayload) {
        JsonNode payload = (JsonNode) rawPayload;

        String content = payload.has("content")
                ? payload.get("content").asText() : "";
        String sessionId = payload.has("sessionId")
                ? payload.get("sessionId").asText() : "desktop-default";

        HostContext hostContext = new HostContext();
        hostContext.setHostType("desktop");

        if (payload.has("hostContext")) {
            JsonNode hc = payload.get("hostContext");
            if (hc.has("clipboardContent")) {
                hostContext.setClipboardContent(hc.get("clipboardContent").asText());
            }
            if (hc.has("activeWindowTitle")) {
                hostContext.setActiveWindowTitle(hc.get("activeWindowTitle").asText());
            }
            if (hc.has("activeWindowProcess")) {
                hostContext.setActiveWindowProcess(hc.get("activeWindowProcess").asText());
            }
            if (hc.has("selectedFilePaths") && hc.get("selectedFilePaths").isArray()) {
                List<String> paths = new ArrayList<>();
                for (JsonNode p : hc.get("selectedFilePaths")) {
                    paths.add(p.asText());
                }
                hostContext.setSelectedFilePaths(paths);
            }
            if (hc.has("screenshotBase64")) {
                hostContext.setScreenshotBase64(hc.get("screenshotBase64").asText());
            }
            if (hc.has("ocrText")) {
                hostContext.setOcrText(hc.get("ocrText").asText());
            }
        }

        return ChannelMessage.builder()
                .channelType("desktop")
                .senderId(payload.has("userId") ? payload.get("userId").asText() : "desktop-user")
                .content(content)
                .chatType(ChannelMessage.ChatType.HOST)
                .platformSessionId(sessionId)
                .hostContext(hostContext)
                .raw(new HashMap<>() {{ put("payload", payload); }})
                .build();
    }

    /**
     * 发送回复到桌面客户端。
     * 支持文本消息、系统通知、文件操作。
     */
    @Override
    public Mono<Void> sendReply(ChannelReply reply) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "reply");
        message.put("channelType", "desktop");
        message.put("content", reply.getContent());
        message.put("hostAction", reply.getHostAction() != null ? reply.getHostAction() : "SHOW_MESSAGE");

        if (reply.getNotificationTitle() != null) {
            message.put("notificationTitle", reply.getNotificationTitle());
            message.put("notificationBody", reply.getNotificationBody());
        }

        if (reply.getFileEdits() != null && !reply.getFileEdits().isEmpty()) {
            List<Map<String, Object>> edits = new ArrayList<>();
            for (ChannelReply.FileEdit edit : reply.getFileEdits()) {
                Map<String, Object> editMap = new LinkedHashMap<>();
                editMap.put("filePath", edit.getFilePath());
                editMap.put("diff", edit.getDiff());
                editMap.put("startLine", edit.getStartLine());
                editMap.put("endLine", edit.getEndLine());
                edits.add(editMap);
            }
            message.put("fileEdits", edits);
        }

        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(message);
            wsSessionManager.broadcast(json);
            log.info("[DesktopHost] Reply sent to desktop client");
        } catch (Exception e) {
            log.error("[DesktopHost] Failed to serialize reply: {}", e.getMessage());
        }

        return Mono.empty();
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return Mono.just(true);
    }
}