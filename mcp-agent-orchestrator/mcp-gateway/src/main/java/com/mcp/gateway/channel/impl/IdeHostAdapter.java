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
 * IDE Host 适配器 — Agent 的 IDE 感知入口。
 * 提供：当前文件、Git 状态、诊断信息、终端输出、代码补全、Diff apply。
 * 与 Rider/VS Code 插件双向通信。
 */
@Slf4j
@Setter
@Component
@ConfigurationProperties(prefix = "channel.ide")
public class IdeHostAdapter implements ChannelAdapter {

    private boolean enabled = true;
    private String systemPrompt = "你是一个 IDE 开发助手，可以感知当前项目、代码文件和 Git 状态。";

    private final WebSocketSessionManager wsSessionManager;

    public IdeHostAdapter(WebSocketSessionManager wsSessionManager) {
        this.wsSessionManager = wsSessionManager;
    }

    @Override
    public String getChannelType() {
        return "ide";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void start() {
        log.info("[IdeHost] Started - IDE Host Adapter is ready");
    }

    @Override
    public void stop() {
        log.info("[IdeHost] Stopped");
    }

    @Override
    public String getSystemPrompt() {
        return systemPrompt;
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("channel", "ide");
        status.put("enabled", enabled);
        status.put("connected", true);
        return status;
    }

    /**
     * 将 IDE 插件发来的 JSON 消息转换为通用 ChannelMessage。
     * 消息格式：
     * {
     *   "type": "message",
     *   "content": "帮我优化这段代码",
     *   "hostContext": {
     *     "currentFilePath": "C:\\project\\src\\Main.java",
     *     "currentFileContent": "public class Main { ... }",
     *     "cursorLine": 42,
     *     "selectedCode": "int x = 1;",
     *     "projectPath": "C:\\project",
     *     "projectFiles": ["src/Main.java", "src/Util.java", ...],
     *     "gitBranch": "main",
     *     "gitStatus": "M src/Main.java\n?? new-file.txt",
     *     "gitDiff": "diff --git ...",
     *     "diagnostics": [
     *       {"filePath": "src/Main.java", "severity": "WARNING", "line": 42, "message": "unused variable"}
     *     ],
     *     "terminalCwd": "C:\\project",
     *     "terminalOutput": "BUILD SUCCESS",
     *     "ideType": "Rider",
     *     "language": "java"
     *   }
     * }
     */
    @Override
    public ChannelMessage normalize(Object rawPayload) {
        JsonNode payload = (JsonNode) rawPayload;

        String content = payload.has("content")
                ? payload.get("content").asText() : "";
        String sessionId = payload.has("sessionId")
                ? payload.get("sessionId").asText() : "ide-default";

        HostContext hostContext = buildHostContext(payload);

        return ChannelMessage.builder()
                .channelType("ide")
                .senderId(payload.has("userId") ? payload.get("userId").asText() : "ide-user")
                .content(content)
                .chatType(ChannelMessage.ChatType.HOST)
                .platformSessionId(sessionId)
                .hostContext(hostContext)
                .raw(new HashMap<>() {{ put("payload", payload); }})
                .build();
    }

    private HostContext buildHostContext(JsonNode payload) {
        HostContext hostContext = new HostContext();
        hostContext.setHostType("ide");

        if (!payload.has("hostContext")) return hostContext;

        JsonNode hc = payload.get("hostContext");

        if (hc.has("currentFilePath")) {
            hostContext.setCurrentFilePath(hc.get("currentFilePath").asText());
        }
        if (hc.has("currentFileContent")) {
            hostContext.setCurrentFileContent(hc.get("currentFileContent").asText());
        }
        if (hc.has("cursorLine")) {
            hostContext.setCursorLine(hc.get("cursorLine").asInt());
        }
        if (hc.has("selectedCode")) {
            hostContext.setSelectedCode(hc.get("selectedCode").asText());
        }
        if (hc.has("projectPath")) {
            hostContext.setProjectPath(hc.get("projectPath").asText());
        }
        if (hc.has("projectFiles") && hc.get("projectFiles").isArray()) {
            List<String> files = new ArrayList<>();
            for (JsonNode f : hc.get("projectFiles")) {
                files.add(f.asText());
            }
            hostContext.setProjectFiles(files);
        }
        if (hc.has("gitBranch")) {
            hostContext.setGitBranch(hc.get("gitBranch").asText());
        }
        if (hc.has("gitStatus")) {
            hostContext.setGitStatus(hc.get("gitStatus").asText());
        }
        if (hc.has("gitDiff")) {
            hostContext.setGitDiff(hc.get("gitDiff").asText());
        }
        if (hc.has("diagnostics") && hc.get("diagnostics").isArray()) {
            List<HostContext.DiagnosticEntry> diagnostics = new ArrayList<>();
            for (JsonNode d : hc.get("diagnostics")) {
                HostContext.DiagnosticEntry entry = new HostContext.DiagnosticEntry();
                entry.setFilePath(d.has("filePath") ? d.get("filePath").asText() : "");
                entry.setSeverity(d.has("severity") ? d.get("severity").asText() : "INFO");
                entry.setLine(d.has("line") ? d.get("line").asInt() : 0);
                entry.setMessage(d.has("message") ? d.get("message").asText() : "");
                diagnostics.add(entry);
            }
            hostContext.setDiagnostics(diagnostics);
        }
        if (hc.has("terminalCwd")) {
            hostContext.setTerminalCwd(hc.get("terminalCwd").asText());
        }
        if (hc.has("terminalOutput")) {
            hostContext.setTerminalOutput(hc.get("terminalOutput").asText());
        }
        if (hc.has("ideType")) {
            hostContext.setIdeType(hc.get("ideType").asText());
        }
        if (hc.has("language")) {
            hostContext.setLanguage(hc.get("language").asText());
        }

        return hostContext;
    }

    /**
     * 发送回复到 IDE 插件。
     * 支持代码修改（Diff apply）、终端命令、诊断修复。
     */
    @Override
    public Mono<Void> sendReply(ChannelReply reply) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "reply");
        message.put("channelType", "ide");
        message.put("content", reply.getContent());
        message.put("hostAction", reply.getHostAction() != null ? reply.getHostAction() : "SHOW_MESSAGE");

        if (reply.getTerminalCommand() != null) {
            message.put("terminalCommand", reply.getTerminalCommand());
        }

        if (reply.getFileEdits() != null && !reply.getFileEdits().isEmpty()) {
            List<Map<String, Object>> edits = new ArrayList<>();
            for (ChannelReply.FileEdit edit : reply.getFileEdits()) {
                Map<String, Object> editMap = new LinkedHashMap<>();
                editMap.put("filePath", edit.getFilePath());
                editMap.put("diff", edit.getDiff());
                editMap.put("startLine", edit.getStartLine());
                editMap.put("endLine", edit.getEndLine());
                if (edit.getNewContent() != null) {
                    editMap.put("newContent", edit.getNewContent());
                }
                edits.add(editMap);
            }
            message.put("fileEdits", edits);
        }

        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(message);
            wsSessionManager.broadcast(json);
            log.info("[IdeHost] Reply sent to IDE client ({} edits, {})",
                    reply.getFileEdits() != null ? reply.getFileEdits().size() : 0,
                    reply.getTerminalCommand() != null ? "cmd: " + reply.getTerminalCommand() : "no cmd");
        } catch (Exception e) {
            log.error("[IdeHost] Failed to serialize reply: {}", e.getMessage());
        }

        return Mono.empty();
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return Mono.just(true);
    }
}