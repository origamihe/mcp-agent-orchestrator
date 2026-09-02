package com.mcp.engine.orchestrator;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Orchestrator 共享常量 — 消除 Magic String 和分散的正则模式。
 *
 * 包含 Agent ID、BuildContext Extension Key、文件路径模式等。
 */
public final class OrchestratorConstants {

    private OrchestratorConstants() {
    }

    /** Agent 注册 ID */
    public static final String AGENT_SEARCH = "search-agent";
    public static final String AGENT_CHAT = "chat-agent";
    public static final String AGENT_CODE = "code-agent";

    /** BuildContext Extension Key — 用于跨组件传递上下文数据 */
    public static final String EXTENSION_ARTIFACT_CONTEXT = "artifactContext";
    public static final String EXTENSION_HISTORY_CONTEXT = "historyContext";
    public static final String EXTENSION_MEMORY_CONTEXT = "memoryContext";
    public static final String EXTENSION_HOST_CONTEXT = "hostContext";
    public static final String EXTENSION_FILE_CONTEXT = "fileContext";
    public static final String EXTENSION_WORKSPACE_CONTEXT = "workspaceContext";
    public static final String EXTENSION_GROUP_CONVERSATION_CONTEXT = "groupConversationContext";

    /** 编排器限制 */
    public static final int MAX_PLAN_STEPS = 16;
    public static final int MIN_ARTIFACT_RESPONSE_LENGTH = 200;
    public static final int MIN_MEMORY_LIFECYCLE_REQUEST_LENGTH = 10;
    public static final int MIN_MEMORY_LIFECYCLE_TOTAL_LENGTH = 300;

    /** 文件路径模式 */
    public static final Pattern WINDOWS_PATH_PATTERN =
            Pattern.compile("[A-Za-z]:\\\\\\S+", Pattern.CASE_INSENSITIVE);

    public static final Pattern FILENAME_PATTERN =
            Pattern.compile("[^\\s.,;:!?，。；：！？\"'<>`|]+\\.\\w{1,10}", Pattern.CASE_INSENSITIVE);

    public static final Pattern FOLLOW_UP_REFERENCE_PATTERN =
            Pattern.compile("(这个|那个|它|其|该|上次|刚刚|刚才).*(?:文件|prompt|代码|文档|内容)",
                    Pattern.CASE_INSENSITIVE);

    /** 文本文件扩展名（用于文件预加载） */
    public static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".txt", ".md", ".java", ".py", ".js", ".ts", ".json", ".xml",
            ".yaml", ".yml", ".properties", ".gradle", ".html", ".css", ".sql",
            ".sh", ".bat", ".cfg", ".conf", ".ini", ".log", ".csv", ".kt",
            ".go", ".rs", ".c", ".cpp", ".h", ".hpp", ".cs", ".php", ".rb", ".scala"
    );

    /** 文档文件扩展名（用于制品召回） */
    public static final Set<String> DOCUMENT_EXTENSIONS = Set.of(".docx", ".pdf");
}