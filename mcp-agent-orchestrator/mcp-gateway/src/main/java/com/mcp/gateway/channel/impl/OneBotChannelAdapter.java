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
    public String getBotUserId() {
        return qqNumber;
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
     * <p>
     * 统一 CQ Code 解析策略：
     * 无论 message 是 string（CQ code）还是 array（segment），
     * 均从 raw_message 或 message 中提取 CQ Code 字符串进行统一解析，
     * 确保 mentionedAgent / mentionedUsers / replyToMessageId 始终正确。
     */
    @Override
    public ChannelMessage normalize(Object rawPayload) {
        JsonNode payload = (JsonNode) rawPayload;

        String messageType = payload.has("message_type")
                ? payload.get("message_type").asText() : "private";

        ChannelMessage.ChatType chatType = "group".equals(messageType)
                ? ChannelMessage.ChatType.GROUP
                : ChannelMessage.ChatType.PRIVATE;

        String senderId = extractSenderId(payload);
        String senderName = extractSenderName(payload);
        String groupId = extractGroupId(payload, chatType);
        String messageId = extractMessageId(payload);
        String selfId = extractSelfId(payload);

        CqParseResult parsed = parseCqMessage(payload, selfId);

        String sessionId = chatType == ChannelMessage.ChatType.GROUP
                ? "qq-group-" + groupId
                : "qq-private-" + senderId;

        HostContext hostContext = buildHostContext(payload, senderId, senderName, groupId, chatType);

        log.debug("[OneBot] Normalized: type={} sender={} group={} msgId={} mentionedAgent={} text={}",
                messageType, senderId, groupId, messageId, parsed.mentionedAgent, parsed.text);

        return ChannelMessage.builder()
                .channelType("qq")
                .senderId(senderId)
                .content(parsed.text)
                .chatId(groupId != null ? groupId : senderId)
                .chatType(chatType)
                .platformSessionId(sessionId)
                .hostContext(hostContext)
                .messageId(messageId)
                .mentionedUsers(parsed.mentionedUsers)
                .mentionedAgent(parsed.mentionedAgent)
                .replyToMessageId(parsed.replyToMessageId)
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

    // ==================== 字段提取 ====================

    private String extractSenderId(JsonNode payload) {
        if (payload.has("sender") && payload.get("sender").has("user_id")) {
            JsonNode uid = payload.get("sender").get("user_id");
            return uid.isNumber() ? String.valueOf(uid.asLong()) : uid.asText();
        }
        return "unknown";
    }

    private String extractSenderName(JsonNode payload) {
        if (payload.has("sender") && payload.get("sender").has("nickname")) {
            return payload.get("sender").get("nickname").asText();
        }
        return null;
    }

    private String extractGroupId(JsonNode payload, ChannelMessage.ChatType chatType) {
        if (chatType == ChannelMessage.ChatType.GROUP && payload.has("group_id")) {
            JsonNode gid = payload.get("group_id");
            return gid.isNumber() ? String.valueOf(gid.asLong()) : gid.asText();
        }
        return null;
    }

    private String extractMessageId(JsonNode payload) {
        if (!payload.has("message_id")) return null;
        JsonNode mid = payload.get("message_id");
        if (mid.isNumber()) return String.valueOf(mid.asLong());
        if (mid.isTextual()) return mid.asText();
        return null;
    }

    private String extractSelfId(JsonNode payload) {
        if (payload.has("self_id")) {
            JsonNode sid = payload.get("self_id");
            return sid.isNumber() ? String.valueOf(sid.asLong()) : sid.asText();
        }
        return qqNumber;
    }

    // ==================== 统一 CQ Code 解析 ====================

    /**
     * CQ Code 解析结果。
     */
    private static class CqParseResult {
        final String text;
        final List<String> mentionedUsers;
        final boolean mentionedAgent;
        final String replyToMessageId;

        CqParseResult(String text, List<String> mentionedUsers,
                      boolean mentionedAgent, String replyToMessageId) {
            this.text = text;
            this.mentionedUsers = Collections.unmodifiableList(mentionedUsers);
            this.mentionedAgent = mentionedAgent;
            this.replyToMessageId = replyToMessageId;
        }
    }

    /**
     * 统一解析 OneBot 消息的 CQ Code。
     * <p>
     * 策略：无论 message 是 string 还是 array，始终从 raw_message（始终为 string CQ code）
     * 或 message（string 回退）中提取 CQ Code 进行解析。
     * 同时从 message array 中补充 reply id。
     */
    private CqParseResult parseCqMessage(JsonNode payload, String selfId) {
        String rawCq = extractRawCqString(payload);
        List<String> mentionedUsers = new ArrayList<>();
        boolean mentionedAgent = false;
        String replyToMessageId = null;

        if (rawCq != null) {
            ParseCqResult cqResult = parseCqCodes(rawCq, selfId);
            mentionedUsers = cqResult.mentionedUsers;
            mentionedAgent = cqResult.mentionedAgent;
            replyToMessageId = cqResult.replyToMessageId;
        }

        if (replyToMessageId == null) {
            replyToMessageId = extractReplyFromMessageArray(payload);
        }

        String text = extractText(rawCq, payload);

        return new CqParseResult(text, mentionedUsers, mentionedAgent, replyToMessageId);
    }

    /**
     * 提取原始 CQ Code 字符串。
     * 优先使用 raw_message，其次使用 message（string 格式）。
     */
    private String extractRawCqString(JsonNode payload) {
        if (payload.has("raw_message")) {
            return payload.get("raw_message").asText();
        }
        if (payload.has("message") && payload.get("message").isTextual()) {
            return payload.get("message").asText();
        }
        return null;
    }

    /**
     * 从 CQ Code 字符串中解析 @ 提及、@Agent、回复目标。
     */
    private ParseCqResult parseCqCodes(String rawCq, String selfId) {
        List<String> mentionedUsers = new ArrayList<>();
        boolean mentionedAgent = false;
        String replyToMessageId = null;

        java.util.regex.Matcher matcher = CQ_CODE_PATTERN.matcher(rawCq);
        while (matcher.find()) {
            String type = matcher.group(1);
            String params = matcher.group(2);

            switch (type) {
                case "at":
                    String qq = extractCqParam(params, "qq");
                    if (qq != null) {
                        if ("all".equals(qq)) {
                            mentionedUsers.add("all");
                        } else {
                            mentionedUsers.add(qq);
                            if (selfId != null && selfId.equals(qq)) {
                                mentionedAgent = true;
                            }
                        }
                    }
                    break;
                case "reply":
                    String id = extractCqParam(params, "id");
                    if (id != null && replyToMessageId == null) {
                        replyToMessageId = id;
                    }
                    break;
                default:
                    break;
            }
        }

        return new ParseCqResult(mentionedUsers, mentionedAgent, replyToMessageId);
    }

    private static final java.util.regex.Pattern CQ_CODE_PATTERN =
            java.util.regex.Pattern.compile("\\[CQ:(\\w+),([^\\]]*)\\]");

    /**
     * 从 CQ Code 参数串中提取指定 key 的值。
     * 例如 "qq=123456,name=test" 中提取 qq → "123456"
     */
    private String extractCqParam(String params, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:^|,)(" + java.util.regex.Pattern.quote(key) + ")=([^,]*)")
                .matcher(params);
        return m.find() ? m.group(2) : null;
    }

    /**
     * 从 message array 中补充 reply id（仅当 CQ Code 解析未获取到时回退）。
     */
    private String extractReplyFromMessageArray(JsonNode payload) {
        if (!payload.has("message") || !payload.get("message").isArray()) {
            return null;
        }
        for (JsonNode seg : payload.get("message")) {
            if ("reply".equals(seg.has("type") ? seg.get("type").asText() : "")
                    && seg.has("data") && seg.get("data").has("id")) {
                return seg.get("data").get("id").asText();
            }
        }
        return null;
    }

    /**
     * 提取纯文本：移除 CQ Code 后返回。
     */
    private String extractText(String rawCq, JsonNode payload) {
        if (rawCq != null) {
            String stripped = CQ_CODE_PATTERN.matcher(rawCq).replaceAll("").trim();
            if (!stripped.isEmpty()) {
                return stripped;
            }
        }
        if (payload.has("message") && payload.get("message").isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode seg : payload.get("message")) {
                if ("text".equals(seg.has("type") ? seg.get("type").asText() : "")
                        && seg.has("data") && seg.get("data").has("text")) {
                    sb.append(seg.get("data").get("text").asText());
                }
            }
            String text = sb.toString().trim();
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    /**
     * CQ Code 解析中间结果（可变）。
     */
    private static class ParseCqResult {
        final List<String> mentionedUsers;
        final boolean mentionedAgent;
        final String replyToMessageId;

        ParseCqResult(List<String> mentionedUsers, boolean mentionedAgent, String replyToMessageId) {
            this.mentionedUsers = mentionedUsers;
            this.mentionedAgent = mentionedAgent;
            this.replyToMessageId = replyToMessageId;
        }
    }
}