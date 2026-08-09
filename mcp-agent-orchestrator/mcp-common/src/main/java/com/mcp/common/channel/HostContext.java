package com.mcp.common.channel;

import java.util.List;
import java.util.Map;

/**
 * Host 上下文 — Host 向 Agent 提供的世界感知能力。
 * 不是聊天信息，而是 Host 所感知到的环境状态。
 * 不同 Host 填充不同的字段子集。
 */
public class HostContext {

    private String hostType;

    // === 身份（聊天 Host 用） ===
    private String userId;
    private String userName;
    private String chatId;
    private ChannelMessage.ChatType chatType;
    private List<String> groupMemberIds;

    // === IDE Host 感知 ===
    private String currentFilePath;
    private String currentFileContent;
    private int cursorLine;
    private int cursorColumn;
    private String selectedText;
    private String selectedCode;
    private String projectPath;
    private String ideType;
    private String language;
    private String gitBranch;
    private String gitDiff;
    private String gitStatus;
    private List<String> openFiles;
    private List<String> projectFiles;
    private String terminalOutput;
    private String terminalCwd;
    private String buildOutput;
    private List<DiagnosticEntry> diagnostics;

    // === Desktop Host 感知 ===
    private String clipboardContent;
    private String activeWindowTitle;
    private String activeWindowProcess;
    private List<String> selectedFilePaths;
    private String screenshotBase64;
    private String ocrText;

    // === 通用扩展 ===
    private Map<String, Object> extra;

    public HostContext() {}

    public static HostContext empty() {
        return new HostContext();
    }

    public boolean isEmpty() {
        return hostType == null
                && userId == null
                && currentFilePath == null
                && clipboardContent == null
                && activeWindowTitle == null
                && (extra == null || extra.isEmpty());
    }

    /**
     * 将 Host 上下文渲染为 Prompt 片段。
     * 由 ContextProvider 在构建分层 Prompt 时调用。
     */
    public String buildHostContextPrompt() {
        if (isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("【当前环境】\n");

        if (hostType != null) {
            sb.append("接入平台：").append(hostType).append("\n");
        }

        if (currentFilePath != null) {
            sb.append("当前文件：").append(currentFilePath);
            if (cursorLine > 0) {
                sb.append(" 第").append(cursorLine).append("行");
            }
            sb.append("\n");
        }

        if (ideType != null) {
            sb.append("IDE类型：").append(ideType).append("\n");
        }

        if (language != null) {
            sb.append("语言：").append(language).append("\n");
        }

        if (projectPath != null) {
            sb.append("项目路径：").append(projectPath).append("\n");
        }

        if (selectedCode != null && !selectedCode.isEmpty()) {
            sb.append("选中代码：\n```").append(language != null ? language : "").append("\n")
                    .append(selectedCode).append("\n```\n");
        } else if (selectedText != null && !selectedText.isEmpty()) {
            sb.append("选中文本：\"").append(selectedText).append("\"\n");
        }

        if (currentFileContent != null && !currentFileContent.isEmpty()) {
            sb.append("文件内容：\n```\n").append(currentFileContent).append("\n```\n");
        }

        if (gitBranch != null) {
            sb.append("Git分支：").append(gitBranch).append("\n");
        }

        if (gitDiff != null && !gitDiff.isEmpty()) {
            sb.append("Git Diff：\n```diff\n").append(gitDiff).append("\n```\n");
        }

        if (gitStatus != null && !gitStatus.isEmpty()) {
            sb.append("Git状态：").append(gitStatus).append("\n");
        }

        if (terminalOutput != null && !terminalOutput.isEmpty()) {
            sb.append("终端输出：\n```\n").append(terminalOutput).append("\n```\n");
        }

        if (terminalCwd != null) {
            sb.append("终端目录：").append(terminalCwd).append("\n");
        }

        if (buildOutput != null && !buildOutput.isEmpty()) {
            sb.append("构建输出：\n```\n").append(buildOutput).append("\n```\n");
        }

        if (diagnostics != null && !diagnostics.isEmpty()) {
            sb.append("诊断信息：\n");
            for (DiagnosticEntry d : diagnostics) {
                sb.append("  - [").append(d.severity).append("] ").append(d.filePath)
                        .append(":").append(d.line).append(": ").append(d.message).append("\n");
            }
        }

        if (clipboardContent != null && !clipboardContent.isEmpty()) {
            sb.append("剪贴板内容：\"").append(clipboardContent).append("\"\n");
        }

        if (activeWindowTitle != null) {
            sb.append("当前窗口：").append(activeWindowTitle).append("\n");
        }

        if (projectFiles != null && !projectFiles.isEmpty()) {
            sb.append("项目文件：\n");
            for (String f : projectFiles) {
                sb.append("  - ").append(f).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * IDE 诊断信息条目。
     */
    public static class DiagnosticEntry {
        private String severity;
        private String filePath;
        private int line;
        private String message;

        public DiagnosticEntry() {}

        public DiagnosticEntry(String severity, String filePath, int line, String message) {
            this.severity = severity;
            this.filePath = filePath;
            this.line = line;
            this.message = message;
        }

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public int getLine() { return line; }
        public void setLine(int line) { this.line = line; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    // === Getters & Setters ===

    public String getHostType() { return hostType; }
    public void setHostType(String hostType) { this.hostType = hostType; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public ChannelMessage.ChatType getChatType() { return chatType; }
    public void setChatType(ChannelMessage.ChatType chatType) { this.chatType = chatType; }

    public List<String> getGroupMemberIds() { return groupMemberIds; }
    public void setGroupMemberIds(List<String> groupMemberIds) { this.groupMemberIds = groupMemberIds; }

    public String getCurrentFilePath() { return currentFilePath; }
    public void setCurrentFilePath(String currentFilePath) { this.currentFilePath = currentFilePath; }

    public String getCurrentFileContent() { return currentFileContent; }
    public void setCurrentFileContent(String currentFileContent) { this.currentFileContent = currentFileContent; }

    public int getCursorLine() { return cursorLine; }
    public void setCursorLine(int cursorLine) { this.cursorLine = cursorLine; }

    public int getCursorColumn() { return cursorColumn; }
    public void setCursorColumn(int cursorColumn) { this.cursorColumn = cursorColumn; }

    public String getSelectedText() { return selectedText; }
    public void setSelectedText(String selectedText) { this.selectedText = selectedText; }

    public String getSelectedCode() { return selectedCode; }
    public void setSelectedCode(String selectedCode) { this.selectedCode = selectedCode; }

    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }

    public String getIdeType() { return ideType; }
    public void setIdeType(String ideType) { this.ideType = ideType; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getGitBranch() { return gitBranch; }
    public void setGitBranch(String gitBranch) { this.gitBranch = gitBranch; }

    public String getGitDiff() { return gitDiff; }
    public void setGitDiff(String gitDiff) { this.gitDiff = gitDiff; }

    public String getGitStatus() { return gitStatus; }
    public void setGitStatus(String gitStatus) { this.gitStatus = gitStatus; }

    public List<String> getOpenFiles() { return openFiles; }
    public void setOpenFiles(List<String> openFiles) { this.openFiles = openFiles; }

    public List<String> getProjectFiles() { return projectFiles; }
    public void setProjectFiles(List<String> projectFiles) { this.projectFiles = projectFiles; }

    public String getTerminalOutput() { return terminalOutput; }
    public void setTerminalOutput(String terminalOutput) { this.terminalOutput = terminalOutput; }

    public String getTerminalCwd() { return terminalCwd; }
    public void setTerminalCwd(String terminalCwd) { this.terminalCwd = terminalCwd; }

    public String getBuildOutput() { return buildOutput; }
    public void setBuildOutput(String buildOutput) { this.buildOutput = buildOutput; }

    public List<DiagnosticEntry> getDiagnostics() { return diagnostics; }
    public void setDiagnostics(List<DiagnosticEntry> diagnostics) { this.diagnostics = diagnostics; }

    public String getClipboardContent() { return clipboardContent; }
    public void setClipboardContent(String clipboardContent) { this.clipboardContent = clipboardContent; }

    public String getActiveWindowTitle() { return activeWindowTitle; }
    public void setActiveWindowTitle(String activeWindowTitle) { this.activeWindowTitle = activeWindowTitle; }

    public String getActiveWindowProcess() { return activeWindowProcess; }
    public void setActiveWindowProcess(String activeWindowProcess) { this.activeWindowProcess = activeWindowProcess; }

    public List<String> getSelectedFilePaths() { return selectedFilePaths; }
    public void setSelectedFilePaths(List<String> selectedFilePaths) { this.selectedFilePaths = selectedFilePaths; }

    public String getScreenshotBase64() { return screenshotBase64; }
    public void setScreenshotBase64(String screenshotBase64) { this.screenshotBase64 = screenshotBase64; }

    public String getOcrText() { return ocrText; }
    public void setOcrText(String ocrText) { this.ocrText = ocrText; }

    public Map<String, Object> getExtra() { return extra; }
    public void setExtra(Map<String, Object> extra) { this.extra = extra; }
}