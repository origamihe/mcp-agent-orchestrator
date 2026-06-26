import com.mcp.core.domain.chat.MessageRole;
import com.mcp.core.entity.ChatMessageEntity;
import com.mcp.core.repository.ChatMessageRepository;
import com.mcp.tools.annotation.McpTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 聊天历史工具集 - 让 Agent 可以读取原始对话记录。
 * 解决"复述聊天记录"类问题：memory_packages 是长期记忆摘要，
 * 不适合精确逐条复述，本工具直接从 chat_messages 表读取原始数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatHistoryTool {

    private final ChatMessageRepository chatMessageRepository;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 读取原始对话历史（逐条消息）。
     * 适用场景：用户要求"列出我说过的所有话"、"复述聊天记录"、"回顾刚才的对话"。
     */
    @McpTool(
            name = "read_conversation_history",
            description = "Read the raw conversation history from the database for a given session. "
                    + "Use this when the user asks to recall, list, replay, or enumerate past messages. "
                    + "Returns messages with timestamps, roles, and sequential numbering. "
                    + "Parameters: sessionId (required, the session identifier), "
                    + "role (optional, filter by role: 'user', 'assistant', or 'all' - default 'all'), "
                    + "limit (optional, max messages to return, default 50, max 200).",
            tags = {"chat", "history", "memory", "conversation"}
    )
    public String readConversationHistory(String sessionId, String role, int limit) {
        try {
            if (sessionId == null || sessionId.isBlank()) {
                return fail("sessionId is required", "read_conversation_history");
            }
            if (limit <= 0) limit = 50;
            if (limit > 200) limit = 200;

            String filterRole = (role != null && !role.isBlank()) ? role.toLowerCase() : "all";

            List<ChatMessageEntity> messages;
            if ("user".equals(filterRole)) {
                messages = chatMessageRepository
                        .findBySessionIdAndRoleOrderByCreatedAtAsc(sessionId, MessageRole.USER);
            } else if ("assistant".equals(filterRole)) {
                messages = chatMessageRepository
                        .findBySessionIdAndRoleOrderByCreatedAtAsc(sessionId, MessageRole.ASSISTANT);
            } else {
                messages = chatMessageRepository
                        .findBySessionIdOrderByCreatedAtAsc(sessionId);
            }

            if (messages.isEmpty()) {
                return success("No messages found for session: " + sessionId,
                        "read_conversation_history", 0, "");
            }

            List<ChatMessageEntity> limited = messages.stream()
                    .sorted(Comparator.comparing(ChatMessageEntity::getCreatedAt))
                    .skip(Math.max(0, messages.size() - limit))
                    .collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append("=== 对话历史 (Session: ").append(sessionId).append(") ===\n");
            sb.append("共 ").append(messages.size()).append(" 条消息");
            if (limited.size() < messages.size()) {
                sb.append("，显示最近 ").append(limited.size()).append(" 条");
            }
            sb.append("\n\n");

            int seq = messages.size() - limited.size() + 1;
            for (ChatMessageEntity msg : limited) {
                String timeStr = formatTime(msg.getCreatedAt());
                String roleLabel = switch (msg.getRole()) {
                    case USER -> "用户";
                    case ASSISTANT -> "助手";
                    case SYSTEM -> "系统";
                    case TOOL -> "工具";
                };
                sb.append("[").append(seq++).append("] ")
                        .append(timeStr).append(" | ")
                        .append(roleLabel).append(":\n");
                sb.append(msg.getContent()).append("\n\n");
            }

            log.info("[ChatHistoryTool] read_conversation_history: session={}, role={}, total={}, returned={}",
                    sessionId, filterRole, messages.size(), limited.size());

            return success("Conversation history retrieved", "read_conversation_history",
                    limited.size(), sb.toString());

        } catch (Exception e) {
            log.error("[ChatHistoryTool] read_conversation_history failed: session={}", sessionId, e);
            return fail("Failed to read conversation history: " + e.getMessage(),
                    "read_conversation_history");
        }
    }

    /**
     * 读取对话摘要（精简版，适合快速回顾）。
     * 适用场景：用户问"我们之前聊了什么"、"最近讨论了什么话题"。
     */
    @McpTool(
            name = "read_conversation_summary",
            description = "Get a concise summary of recent conversation history. "
                    + "Use this for general questions like 'what did we discuss' or 'summarize our chat'. "
                    + "Returns only user messages with brief content previews. "
                    + "Parameters: sessionId (required, the session identifier), "
                    + "limit (optional, max messages, default 20).",
            tags = {"chat", "summary", "memory", "conversation"}
    )
    public String readConversationSummary(String sessionId, int limit) {
        try {
            if (sessionId == null || sessionId.isBlank()) {
                return fail("sessionId is required", "read_conversation_summary");
            }
            if (limit <= 0) limit = 20;
            if (limit > 100) limit = 100;

            List<ChatMessageEntity> allMessages = chatMessageRepository
                    .findBySessionIdOrderByCreatedAtAsc(sessionId);

            if (allMessages.isEmpty()) {
                return success("No messages found for session: " + sessionId,
                        "read_conversation_summary", 0, "");
            }

            List<ChatMessageEntity> recent = allMessages.stream()
                    .sorted(Comparator.comparing(ChatMessageEntity::getCreatedAt).reversed())
                    .limit(limit)
                    .sorted(Comparator.comparing(ChatMessageEntity::getCreatedAt))
                    .collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append("=== 对话摘要 (Session: ").append(sessionId).append(") ===\n");
            sb.append("总会话消息数: ").append(allMessages.size()).append("\n");
            sb.append("最近 ").append(recent.size()).append(" 条消息:\n\n");

            int totalUser = 0, totalAssistant = 0;
            int seq = 1;
            for (ChatMessageEntity msg : recent) {
                String timeStr = formatTime(msg.getCreatedAt());
                String content = msg.getContent();
                if (content != null && content.length() > 150) {
                    content = content.substring(0, 150) + "...";
                }
                String roleLabel = switch (msg.getRole()) {
                    case USER -> { totalUser++; yield "用户"; }
                    case ASSISTANT -> { totalAssistant++; yield "助手"; }
                    case SYSTEM -> "系统";
                    case TOOL -> "工具";
                };
                sb.append("[").append(seq++).append("] ")
                        .append(timeStr).append(" | ")
                        .append(roleLabel).append(": ")
                        .append(content).append("\n");
            }

            sb.append("\n---\n");
            sb.append("统计: 用户消息 ").append(totalUser)
                    .append(" 条, 助手消息 ").append(totalAssistant).append(" 条");

            log.info("[ChatHistoryTool] read_conversation_summary: session={}, total={}, returned={}",
                    sessionId, allMessages.size(), recent.size());

            return success("Conversation summary retrieved", "read_conversation_summary",
                    recent.size(), sb.toString());

        } catch (Exception e) {
            log.error("[ChatHistoryTool] read_conversation_summary failed: session={}", sessionId, e);
            return fail("Failed to read conversation summary: " + e.getMessage(),
                    "read_conversation_summary");
        }
    }

    private String formatTime(LocalDateTime time) {
        if (time == null) return "未知时间";
        try {
            return time.format(TIME_FMT);
        } catch (Exception e) {
            return time.toString();
        }
    }

    private String success(String message, String tool, int count, String data) {
        return "{\"success\":true,\"tool\":\"" + escapeJson(tool)
                + "\",\"message\":\"" + escapeJson(message)
                + "\",\"count\":" + count
                + ",\"data\":\"" + escapeJson(data) + "\"}";
    }

    private String fail(String error, String tool) {
        return "{\"success\":false,\"tool\":\"" + escapeJson(tool)
                + "\",\"error\":\"" + escapeJson(error) + "\"}";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}