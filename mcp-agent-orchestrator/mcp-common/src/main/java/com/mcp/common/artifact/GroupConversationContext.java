package com.mcp.common.artifact;

import java.util.ArrayList;
import java.util.List;

/**
 * 群聊对话上下文 — 承载分层群聊上下文，供 Agent Prompt 注入。
 *
 * 层级（从高到低优先级）：
 * <ol>
 *   <li>Current Thread：当前 @Agent 问题及其连续补充消息</li>
 *   <li>Recent Group Context：最近一段群聊，按 createdAt 时间顺序</li>
 *   <li>Relevant Group Memory：语义检索的与当前问题相关的更早群聊历史</li>
 *   <li>User Memory：用户个人长期记忆（由 MemoryRetriever 独立处理）</li>
 *   <li>Current Message：当前触发 Agent 的 @消息（由 BuildContext.userMessage 承载）</li>
 * </ol>
 *
 * 与 {@link ConversationContext} 的区别：
 * ConversationContext 用于追踪当前会话的 Artifact 工作对象（代码/文档等），
 * 本类专用于群聊消息上下文的 Prompt 注入。
 */
public class GroupConversationContext {

    private String groupId;
    private String threadId;

    private String currentThread;
    private String recentGroupContext;
    private String relevantGroupMemory;

    private final List<String> layers = new ArrayList<>();

    public GroupConversationContext() {
    }

    public GroupConversationContext(String groupId, String threadId) {
        this.groupId = groupId;
        this.threadId = threadId;
    }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }

    public String getCurrentThread() { return currentThread; }
    public void setCurrentThread(String currentThread) { this.currentThread = currentThread; }

    public String getRecentGroupContext() { return recentGroupContext; }
    public void setRecentGroupContext(String recentGroupContext) { this.recentGroupContext = recentGroupContext; }

    public String getRelevantGroupMemory() { return relevantGroupMemory; }
    public void setRelevantGroupMemory(String relevantGroupMemory) { this.relevantGroupMemory = relevantGroupMemory; }

    public boolean isEmpty() {
        return (currentThread == null || currentThread.isEmpty())
                && (recentGroupContext == null || recentGroupContext.isEmpty())
                && (relevantGroupMemory == null || relevantGroupMemory.isEmpty());
    }

    /**
     * 按优先级层次格式化为 Prompt 文本。
     * 顺序：Current Thread → Recent Group Context → Relevant Group Memory
     */
    public String toPromptText() {
        if (isEmpty()) return "";

        StringBuilder sb = new StringBuilder();

        if (currentThread != null && !currentThread.isEmpty()) {
            sb.append("【当前对话线程】\n");
            sb.append(currentThread).append("\n\n");
        }

        if (recentGroupContext != null && !recentGroupContext.isEmpty()) {
            sb.append("【最近群聊上下文】\n");
            sb.append(recentGroupContext).append("\n\n");
        }

        if (relevantGroupMemory != null && !relevantGroupMemory.isEmpty()) {
            sb.append("【相关群聊记忆】\n");
            sb.append(relevantGroupMemory).append("\n");
        }

        return sb.toString().trim();
    }

    /**
     * 返回分层数量（用于日志/诊断）。
     */
    public int layerCount() {
        int count = 0;
        if (currentThread != null && !currentThread.isEmpty()) count++;
        if (recentGroupContext != null && !recentGroupContext.isEmpty()) count++;
        if (relevantGroupMemory != null && !relevantGroupMemory.isEmpty()) count++;
        return count;
    }

    /**
     * 创建空上下文。
     */
    public static GroupConversationContext empty() {
        return new GroupConversationContext();
    }
}