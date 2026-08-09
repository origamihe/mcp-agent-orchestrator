package com.mcp.common.workspace;

import com.mcp.common.artifact.Artifact;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 工作空间 — Agent 的长期工作状态。
 * 跨会话、跨 Host 持久化，Agent 启动时恢复。
 * 聊天只是 Workspace 中的一种操作，不是主体。
 */
public class Workspace {

    private String workspaceId;
    private String name;
    private String projectPath;
    private String projectRoot;

    private List<Task> activeTasks = new ArrayList<>();
    private List<Todo> todos = new ArrayList<>();

    private GitState gitState = new GitState();
    private TerminalState terminalState = new TerminalState();

    private List<String> fileTreeSnapshot = new ArrayList<>();

    private Map<String, String> hostContexts = new LinkedHashMap<>();

    private Map<String, OpenedFile> openedFiles = new LinkedHashMap<>();

    private List<Artifact> artifacts = new ArrayList<>();

    private String lastActiveFile;
    private int lastActiveLine;
    private OpenedFile lastOpenedFile;

    private Instant lastActiveAt;
    private Instant createdAt;
    private Instant updatedAt;

    private transient boolean dirty = false;

    public Workspace() {}

    public boolean isEmpty() {
        return workspaceId == null
                && projectPath == null
                && projectRoot == null
                && activeTasks.isEmpty()
                && todos.isEmpty()
                && (gitState == null || gitState.isEmpty())
                && (terminalState == null || terminalState.isEmpty())
                && fileTreeSnapshot.isEmpty()
                && openedFiles.isEmpty()
                && artifacts.isEmpty()
                && lastOpenedFile == null;
    }

    /**
     * 将 Workspace 渲染为 Prompt 片段。
     * 在构建分层 Prompt 时注入，告诉 Agent 当前工作上下文。
     */
    public String buildWorkspacePrompt() {
        if (isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("【工作空间】\n");

        if (name != null) {
            sb.append("项目名称：").append(name).append("\n");
        }
        if (projectRoot != null) {
            sb.append("项目根目录：").append(projectRoot).append("\n");
        } else if (projectPath != null) {
            sb.append("项目路径：").append(projectPath).append("\n");
        }

        if (!activeTasks.isEmpty()) {
            sb.append("活跃任务：\n");
            for (Task t : activeTasks) {
                sb.append("  - ");
                if (t.status != null) sb.append("[").append(t.status).append("] ");
                sb.append(t.title);
                if (t.description != null) sb.append(": ").append(t.description);
                sb.append("\n");
            }
        }

        if (!todos.isEmpty()) {
            sb.append("待办事项：\n");
            for (Todo t : todos) {
                sb.append("  - ");
                if (t.isDone()) sb.append("[✓] ");
                else sb.append("[ ] ");
                sb.append(t.content).append("\n");
            }
        }

        if (lastOpenedFile != null) {
            sb.append("上次打开的文件：").append(lastOpenedFile.toReferenceString()).append("\n");
        } else if (lastActiveFile != null) {
            sb.append("上次操作的文件：").append(lastActiveFile);
            if (lastActiveLine > 0) sb.append(" 第").append(lastActiveLine).append("行");
            sb.append("\n");
        }

        if (!openedFiles.isEmpty()) {
            sb.append("\n【已打开文件内容】\n");
            for (var entry : openedFiles.entrySet()) {
                OpenedFile of = entry.getValue();
                sb.append("--- 文件: ").append(entry.getKey());
                if (of.isReadme) sb.append(" (README)");
                if (of.language != null) sb.append(" [").append(of.language).append("]");
                sb.append(" ---\n");
                sb.append(of.content).append("\n");
                if (of.mtime != null) sb.append("(最后修改: ").append(of.mtime).append(")\n");
                sb.append("\n");
            }
        }

        if (!artifacts.isEmpty()) {
            sb.append("\n【Artifact 工作对象】\n");
            sb.append("以下是你当前会话中的可编辑对象，与长期记忆（Memory）完全独立：\n");
            for (Artifact a : artifacts) {
                sb.append("--- Artifact: ").append(a.getPath() != null ? a.getPath() : a.getId());
                sb.append(" (").append(a.getType()).append(")");
                sb.append(" v").append(a.getVersion());
                sb.append(" ---\n");
                sb.append(a.getContent()).append("\n");
                if (a.getModifiedAt() != null) sb.append("(最后修改: ").append(a.getModifiedAt()).append(")\n");
                sb.append("\n");
            }
        }

        if (gitState != null && !gitState.isEmpty()) {
            sb.append("Git状态：\n");
            if (gitState.branch != null) sb.append("  分支：").append(gitState.branch).append("\n");
            if (gitState.status != null) sb.append("  状态：").append(gitState.status).append("\n");
            if (gitState.lastCommitMessage != null) sb.append("  最近提交：").append(gitState.lastCommitMessage).append("\n");
        }

        if (terminalState != null && !terminalState.isEmpty()) {
            sb.append("终端状态：\n");
            if (terminalState.cwd != null) sb.append("  目录：").append(terminalState.cwd).append("\n");
            if (terminalState.lastCommand != null) sb.append("  上次命令：").append(terminalState.lastCommand).append("\n");
        }

        return sb.toString();
    }

    // === 内嵌类型 ===

    public static class Task {
        private String id;
        private String title;
        private String description;
        private String status;
        private Instant createdAt;

        public Task() {}
        public Task(String id, String title, String description, String status) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.status = status;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    }

    public static class Todo {
        private String content;
        private boolean done;

        public Todo() {}
        public Todo(String content, boolean done) {
            this.content = content;
            this.done = done;
        }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public boolean isDone() { return done; }
        public void setDone(boolean done) { this.done = done; }
    }

    public static class GitState {
        private String branch;
        private String status;
        private String diff;
        private String lastCommitMessage;
        private String lastCommitHash;

        public GitState() {}

        public boolean isEmpty() {
            return branch == null && status == null && diff == null
                    && lastCommitMessage == null && lastCommitHash == null;
        }

        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getDiff() { return diff; }
        public void setDiff(String diff) { this.diff = diff; }
        public String getLastCommitMessage() { return lastCommitMessage; }
        public void setLastCommitMessage(String lastCommitMessage) { this.lastCommitMessage = lastCommitMessage; }
        public String getLastCommitHash() { return lastCommitHash; }
        public void setLastCommitHash(String lastCommitHash) { this.lastCommitHash = lastCommitHash; }
    }

    public static class OpenedFile {
        private String path;
        private String content;
        private String encoding;
        private String language;
        private String fileType;
        private boolean isReadme;
        private Instant mtime;
        private Instant readAt;
        private long size;

        public OpenedFile() {}
        public OpenedFile(String content, String encoding, Instant mtime, long size) {
            this.content = content;
            this.encoding = encoding;
            this.mtime = mtime;
            this.size = size;
        }

        /**
         * 构建引用字符串，用于 Prompt 注入。
         * 例如："README.md (README) [markdown] — 最后读取于 2024-01-01"
         */
        public String toReferenceString() {
            StringBuilder sb = new StringBuilder();
            sb.append(path != null ? path : "(unknown)");
            if (isReadme) sb.append(" (README)");
            if (language != null) sb.append(" [").append(language).append("]");
            if (readAt != null) sb.append(" — 最后读取于 ").append(readAt);
            if (mtime != null) sb.append(" — 最后修改于 ").append(mtime);
            return sb.toString();
        }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getEncoding() { return encoding; }
        public void setEncoding(String encoding) { this.encoding = encoding; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
        public boolean isReadme() { return isReadme; }
        public void setReadme(boolean readme) { isReadme = readme; }
        public Instant getMtime() { return mtime; }
        public void setMtime(Instant mtime) { this.mtime = mtime; }
        public Instant getReadAt() { return readAt; }
        public void setReadAt(Instant readAt) { this.readAt = readAt; }
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
    }

    public static class TerminalState {
        private String cwd;
        private String lastCommand;
        private String lastOutput;
        private int exitCode;

        public TerminalState() {}

        public boolean isEmpty() {
            return cwd == null && lastCommand == null && lastOutput == null;
        }

        public String getCwd() { return cwd; }
        public void setCwd(String cwd) { this.cwd = cwd; }
        public String getLastCommand() { return lastCommand; }
        public void setLastCommand(String lastCommand) { this.lastCommand = lastCommand; }
        public String getLastOutput() { return lastOutput; }
        public void setLastOutput(String lastOutput) { this.lastOutput = lastOutput; }
        public int getExitCode() { return exitCode; }
        public void setExitCode(int exitCode) { this.exitCode = exitCode; }
    }

    // === Getters & Setters ===

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }

    public String getProjectRoot() { return projectRoot; }
    public void setProjectRoot(String projectRoot) { this.projectRoot = projectRoot; }

    public List<Task> getActiveTasks() { return activeTasks; }
    public void setActiveTasks(List<Task> activeTasks) { this.activeTasks = activeTasks; }

    public List<Todo> getTodos() { return todos; }
    public void setTodos(List<Todo> todos) { this.todos = todos; }

    public GitState getGitState() { return gitState; }
    public void setGitState(GitState gitState) { this.gitState = gitState; }

    public TerminalState getTerminalState() { return terminalState; }
    public void setTerminalState(TerminalState terminalState) { this.terminalState = terminalState; }

    public List<String> getFileTreeSnapshot() { return fileTreeSnapshot; }
    public void setFileTreeSnapshot(List<String> fileTreeSnapshot) { this.fileTreeSnapshot = fileTreeSnapshot; }

    public Map<String, String> getHostContexts() { return hostContexts; }
    public void setHostContexts(Map<String, String> hostContexts) { this.hostContexts = hostContexts; }

    public Map<String, OpenedFile> getOpenedFiles() { return openedFiles; }
    public void setOpenedFiles(Map<String, OpenedFile> openedFiles) { this.openedFiles = openedFiles; }

    public Optional<OpenedFile> getOpenedFile(String path) {
        return Optional.ofNullable(openedFiles.get(path));
    }

    /**
     * 打开文件并记录到 Workspace。
     * 自动检测 README 和语言类型。
     */
    public void openFile(String path, String content, String encoding, Instant mtime, long size) {
        OpenedFile file = new OpenedFile(content, encoding, mtime, size);
        file.setPath(path);
        file.setReadAt(Instant.now());
        file.setLanguage(detectLanguage(path));
        file.setReadme(detectIsReadme(path));
        this.openedFiles.put(path, file);
        this.lastActiveFile = path;
        this.lastOpenedFile = file;
        this.lastActiveAt = Instant.now();
    }

    /**
     * 关闭文件（从 openedFiles 中移除）。
     */
    public void closeFile(String path) {
        this.openedFiles.remove(path);
        if (path.equals(lastActiveFile)) {
            this.lastActiveFile = null;
        }
    }

    /**
     * 追踪 README 文件（自动标记 isReadme=true）。
     */
    public void trackReadme(String path, String content) {
        OpenedFile file = new OpenedFile(content, "UTF-8", Instant.now(), content != null ? content.length() : 0);
        file.setPath(path);
        file.setReadAt(Instant.now());
        file.setLanguage("markdown");
        file.setReadme(true);
        this.openedFiles.put(path, file);
        this.lastActiveFile = path;
        this.lastOpenedFile = file;
        this.lastActiveAt = Instant.now();
    }

    /**
     * 获取最后打开的文件引用。
     */
    public Optional<OpenedFile> getLastOpenedFile() {
        return Optional.ofNullable(lastOpenedFile);
    }

    /**
     * 获取已打开的 README 文件。
     */
    public Optional<OpenedFile> getOpenedReadme() {
        return openedFiles.values().stream()
                .filter(OpenedFile::isReadme)
                .findFirst();
    }

    public void addOpenedFile(String path, String content, String encoding, Instant mtime, long size) {
        openFile(path, content, encoding, mtime, size);
    }

    public Optional<String> getLastActiveFileContent() {
        if (lastActiveFile == null) return Optional.empty();
        return getOpenedFile(lastActiveFile).map(OpenedFile::getContent);
    }

    public List<Artifact> getArtifacts() { return artifacts; }
    public void setArtifacts(List<Artifact> artifacts) { this.artifacts = artifacts; }

    public void addArtifact(Artifact artifact) {
        this.artifacts.add(artifact);
        this.lastActiveFile = artifact.getPath();
    }

    public Optional<Artifact> getArtifact(String path) {
        return artifacts.stream()
                .filter(a -> path.equals(a.getPath()))
                .findFirst();
    }

    public String getLastActiveFile() { return lastActiveFile; }
    public void setLastActiveFile(String lastActiveFile) { this.lastActiveFile = lastActiveFile; }

    public int getLastActiveLine() { return lastActiveLine; }
    public void setLastActiveLine(int lastActiveLine) { this.lastActiveLine = lastActiveLine; }

    public void setLastOpenedFile(OpenedFile lastOpenedFile) { this.lastOpenedFile = lastOpenedFile; }

    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean isDirty() { return dirty; }
    public void markDirty() { this.dirty = true; }
    public void markClean() { this.dirty = false; }

    private static String detectLanguage(String path) {
        if (path == null) return null;
        String lower = path.toLowerCase();
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".js")) return "javascript";
        if (lower.endsWith(".ts")) return "typescript";
        if (lower.endsWith(".go")) return "go";
        if (lower.endsWith(".rs")) return "rust";
        if (lower.endsWith(".kt")) return "kotlin";
        if (lower.endsWith(".scala")) return "scala";
        if (lower.endsWith(".cs")) return "csharp";
        if (lower.endsWith(".c") || lower.endsWith(".h")) return "c";
        if (lower.endsWith(".cpp") || lower.endsWith(".hpp")) return "cpp";
        if (lower.endsWith(".sql")) return "sql";
        if (lower.endsWith(".md")) return "markdown";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "yaml";
        if (lower.endsWith(".html")) return "html";
        if (lower.endsWith(".css")) return "css";
        if (lower.endsWith(".sh") || lower.endsWith(".bat")) return "shell";
        if (lower.endsWith(".properties")) return "properties";
        if (lower.endsWith(".gradle")) return "gradle";
        return null;
    }

    private static boolean detectIsReadme(String path) {
        if (path == null) return false;
        String name = path.toLowerCase();
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf('/') + 1);
        }
        if (name.contains("\\")) {
            name = name.substring(name.lastIndexOf('\\') + 1);
        }
        return name.startsWith("readme") || name.equals("readme.md")
                || name.equals("readme.txt") || name.equals("readme.rst");
    }
}