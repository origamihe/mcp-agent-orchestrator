package com.mcp.gateway.channel.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcp.common.channel.ChannelMessage;
import com.mcp.common.channel.ChannelReply;
import com.mcp.common.channel.HostContext;
import com.mcp.gateway.channel.ChannelAdapter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

@Slf4j
@Setter
@Component
@ConfigurationProperties(prefix = "channel.qq")
public class OneBotChannelAdapter implements ChannelAdapter {

    private boolean enabled;
    private String onebotUrl;
    private String accessToken;
    private String qqNumber;
    private String replyMode = "all";
    private String keywords = "";
    private String systemPrompt = "你是一个专业、友好的智能助手。";

    @Override
    public String getChannelType() { return "qq"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void start() {
        log.info("[OneBot Adapter] Started - QQ: {}, URL: {}", qqNumber, onebotUrl);
    }

    @Override
    public void stop() {
        log.info("[OneBot Adapter] Stopped");
    }

    @Override
    public String getSystemPrompt() {
        return systemPrompt;
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("channel", "qq");
        status.put("enabled", enabled);
        status.put("onebotUrl", onebotUrl);
        return status;
    }

    /**
     * OneBot v11 消息 → 通用 ChannelMessage。
     * 填充 HostContext 以支持 QQ Host 的身份感知和工作空间关联。
     */
    @Override
    public ChannelMessage normalize(Object rawPayload) {
        JsonNode payload = (JsonNode) rawPayload;
        String messageType = payload.has("message_type")
                ? payload.get("message_type").asText() : "private";
        String senderId = payload.has("sender")
                ? payload.get("sender").get("user_id").asText() : "unknown";
        String senderName = payload.has("sender") && payload.get("sender").has("nickname")
                ? payload.get("sender").get("nickname").asText() : null;
        String groupId = "group".equals(messageType) && payload.has("group_id")
                ? payload.get("group_id").asText() : null;

        String content = extractText(payload);

        ChannelMessage.ChatType chatType = "group".equals(messageType)
                ? ChannelMessage.ChatType.GROUP
                : ChannelMessage.ChatType.PRIVATE;

        String sessionId = chatType == ChannelMessage.ChatType.GROUP
                ? "qq-group-" + groupId
                : "qq-private-" + senderId;

        HostContext hostContext = buildHostContext(payload, senderId, senderName, groupId, chatType);

        return ChannelMessage.builder()
                .channelType("qq")
                .senderId(senderId)
                .content(content)
                .chatId(groupId != null ? groupId : senderId)
                .chatType(chatType)
                .platformSessionId(sessionId)
                .hostContext(hostContext)
                .raw(new HashMap<>() {{ put("payload", payload); }})
                .build();
    }

    /**
     * 构建 QQ Host 的 HostContext。
     * QQ 作为聊天 Host，提供身份、群组上下文等感知能力。
     */
    private HostContext buildHostContext(JsonNode payload, String senderId, String senderName,
                                          String groupId, ChannelMessage.ChatType chatType) {
        HostContext hostContext = new HostContext();
        hostContext.setHostType("qq");
        hostContext.setUserId(senderId);
        hostContext.setUserName(senderName);
        hostContext.setChatId(groupId != null ? groupId : senderId);
        hostContext.setChatType(chatType);

        if (chatType == ChannelMessage.ChatType.GROUP && groupId != null) {
            hostContext.setGroupMemberIds(new ArrayList<>());
        }

        return hostContext;
    }

    /**
     * OneBot API 发送消息（支持文本 + 语音）
     */
    @Override
    public Mono<Void> sendReply(ChannelReply reply) {
        if (reply.isSendAsFile() && reply.getFilePath() != null) {
            return sendFileReply(reply);
        }
        if (reply.isSendAsVoice() && reply.getVoiceData() != null && reply.getVoiceData().length > 0) {
            return sendVoiceReply(reply);
        }
        return sendTextReply(reply);
    }

    private Mono<Void> sendTextReply(ChannelReply reply) {
        String content = reply.getContent();
        if (content == null || content.isBlank()) {
            log.warn("[OneBot] Skipping empty message to {}", reply.getTargetId());
            return Mono.empty();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message_type", reply.getChatType() == ChannelMessage.ChatType.GROUP ? "group" : "private");
        body.put(reply.getChatType() == ChannelMessage.ChatType.GROUP ? "group_id" : "user_id", reply.getTargetId());
        body.put("message", content);
        body.put("auto_escape", false);

        return buildWebClient()
                .post()
                .uri("/send_msg")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(r -> log.info("[OneBot] Sent text to {}", reply.getTargetId()))
                .doOnError(e -> log.error("[OneBot] Send failed: {}", e.getMessage()))
                .then();
    }

    private Mono<Void> sendVoiceReply(ChannelReply reply) {
        String base64Voice = Base64.getEncoder().encodeToString(reply.getVoiceData());
        String cqCode = "[CQ:record,file=base64://" + base64Voice + "]";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message_type", reply.getChatType() == ChannelMessage.ChatType.GROUP ? "group" : "private");
        body.put(reply.getChatType() == ChannelMessage.ChatType.GROUP ? "group_id" : "user_id", reply.getTargetId());
        body.put("message", cqCode);
        body.put("auto_escape", false);

        return buildWebClient()
                .post()
                .uri("/send_msg")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(r -> log.info("[OneBot] Sent voice to {}", reply.getTargetId()))
                .doOnError(e -> log.error("[OneBot] Voice send failed: {}", e.getMessage()))
                .then();
    }

    private Mono<Void> sendFileReply(ChannelReply reply) {
        boolean isGroup = reply.getChatType() == ChannelMessage.ChatType.GROUP;
        String apiPath = isGroup ? "/upload_group_file" : "/upload_private_file";
        String targetKey = isGroup ? "group_id" : "user_id";

        String fileName = reply.getFilePath().substring(
                reply.getFilePath().replace('\\', '/').lastIndexOf('/') + 1);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put(targetKey, reply.getTargetId());
        body.put("file", reply.getFilePath());
        body.put("name", fileName);

        return buildWebClient()
                .post()
                .uri(apiPath)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(r -> log.info("[OneBot] File uploaded: {} to {}", fileName, reply.getTargetId()))
                .doOnError(e -> log.error("[OneBot] File upload failed: {}", e.getMessage()))
                .then();
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return buildWebClient()
                .get().uri("/get_login_info")
                .retrieve().bodyToMono(Map.class)
                .map(r -> true)
                .onErrorReturn(false);
    }

    private WebClient buildWebClient() {
        return WebClient.builder()
                .baseUrl(onebotUrl)
                .defaultHeader("Authorization", "Bearer " + (accessToken != null ? accessToken : ""))
                .build();
    }

    private String extractText(JsonNode payload) {
        if (payload.has("raw_message")) {
            return payload.get("raw_message").asText()
                    .replaceAll("\\[CQ:[^]]+\\]", "").trim();
        }
        if (payload.has("message")) {
            JsonNode msgNode = payload.get("message");
            if (msgNode.isTextual()) return msgNode.asText();
            if (msgNode.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode seg : msgNode) {
                    if (seg.has("data") && seg.get("data").has("text")) {
                        sb.append(seg.get("data").get("text").asText());
                    }
                }
                return sb.toString().trim();
            }
        }
        return null;
    }
}