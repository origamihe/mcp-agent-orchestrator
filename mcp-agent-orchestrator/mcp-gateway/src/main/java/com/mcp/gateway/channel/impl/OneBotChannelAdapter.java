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
     * 解析 CQ:at 信息以支持 @Agent 检测和多用户调度。
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

        ParsedMessage parsed = parseMessage(payload);
        String content = parsed.text;
        List<String> mentionedUsers = parsed.mentionedUsers;
        boolean mentionedAgent = parsed.mentionedAgent;
        String messageId = payload.has("message_id") ? String.valueOf(payload.get("message_id").asLong()) : null;
        String replyToMessageId = parsed.replyToMessageId;

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
                .messageId(messageId)
                .mentionedUsers(mentionedUsers)
                .mentionedAgent(mentionedAgent)
                .replyToMessageId(replyToMessageId)
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

        String message = content;
        if (reply.getMentionTargetId() != null && !reply.getMentionTargetId().isBlank()
                && reply.getChatType() == ChannelMessage.ChatType.GROUP) {
            message = "[CQ:at,qq=" + reply.getMentionTargetId() + "] " + content;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message_type", reply.getChatType() == ChannelMessage.ChatType.GROUP ? "group" : "private");
        body.put(reply.getChatType() == ChannelMessage.ChatType.GROUP ? "group_id" : "user_id", reply.getTargetId());
        body.put("message", message);
        body.put("auto_escape", false);

        return buildWebClient()
                .post()
                .uri("/send_msg")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(r -> log.info("[OneBot] Sent text to {} (mention={})",
                        reply.getTargetId(), reply.getMentionTargetId()))
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

    /**
     * 解析 OneBot v11 消息数组，提取纯文本、@ 提及列表、是否 @Bot、回复消息ID。
     * OneBot 消息格式：
     * <pre>
     *   "message": [
     *     {"type": "at", "data": {"qq": "123456"}},
     *     {"type": "text", "data": {"text": "你好"}},
     *     {"type": "reply", "data": {"id": "789"}}
     *   ]
     * </pre>
     */
    private ParsedMessage parseMessage(JsonNode payload) {
        List<String> mentionedUsers = new ArrayList<>();
        boolean mentionedAgent = false;
        String replyToMessageId = null;
        StringBuilder textBuilder = new StringBuilder();

        if (payload.has("message")) {
            JsonNode msgNode = payload.get("message");
            if (msgNode.isTextual()) {
                textBuilder.append(msgNode.asText());
            } else if (msgNode.isArray()) {
                for (JsonNode seg : msgNode) {
                    String type = seg.has("type") ? seg.get("type").asText() : "";
                    JsonNode data = seg.has("data") ? seg.get("data") : null;

                    switch (type) {
                        case "at":
                            if (data != null && data.has("qq")) {
                                String atQq = data.get("qq").asText();
                                if ("all".equals(atQq)) {
                                    mentionedUsers.add("all");
                                } else {
                                    mentionedUsers.add(atQq);
                                    if (qqNumber != null && qqNumber.equals(atQq)) {
                                        mentionedAgent = true;
                                    }
                                }
                            }
                            break;
                        case "text":
                            if (data != null && data.has("text")) {
                                textBuilder.append(data.get("text").asText());
                            }
                            break;
                        case "reply":
                            if (data != null && data.has("id")) {
                                replyToMessageId = data.get("id").asText();
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        }

        String text = textBuilder.toString().trim();
        if (text.isEmpty() && payload.has("raw_message")) {
            text = payload.get("raw_message").asText()
                    .replaceAll("\\[CQ:[^]]+\\]", "").trim();
        }

        return new ParsedMessage(text, mentionedUsers, mentionedAgent, replyToMessageId);
    }

    /**
     * 解析后的消息结构。
     */
    private record ParsedMessage(
            String text,
            List<String> mentionedUsers,
            boolean mentionedAgent,
            String replyToMessageId) {
    }
}