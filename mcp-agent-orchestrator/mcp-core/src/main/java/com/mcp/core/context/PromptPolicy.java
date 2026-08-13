package com.mcp.core.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Prompt 策略 — 决定不同的 Agent 使用哪些 PromptLayer。
 *
 * 设计原则：
 * 1. Policy 是业务决策者，Assembler 是纯渲染器
 * 2. 不同 Agent 类型使用不同的 Layer 组合
 * 3. 新增 Policy 无需修改 Assembler 或任何 Provider
 *
 * 典型场景：
 * - ChatAgent：需要 Persona、Memory、Workspace 等完整上下文
 * - CodeAgent：不需要 Persona，只需要 Safety、Workspace、Skill、Plan
 * - SearchAgent：不需要 Persona，需要 Workspace、Memory、Knowledge
 * - RoleplayAgent：需要 Persona、WorldState、GroupContext 等角色相关层
 */
public enum PromptPolicy {

    CHAT_LIGHT(Set.of(
            "MODE_LOCK",
            "IDENTITY", "CHARACTER",
            "GROUP_CONVERSATION", "MEMORY")),

    CHAT(Set.of(
            "MODE_LOCK", "WORLD_STATE",
            "IDENTITY", "CHARACTER", "RELATIONSHIP",
            "GROUP_CONTEXT", "GROUP_CONVERSATION", "USER_PROFILE",
            "WORKSPACE", "HOST_CONTEXT", "MEMORY", "ARTIFACT", "PLAN", "MODE_HINT")),

    CODE(Set.of(
            "MODE_LOCK", "WORKSPACE", "HOST_CONTEXT", "MEMORY", "ARTIFACT", "PLAN")),

    SEARCH(Set.of(
            "MODE_LOCK", "WORKSPACE", "HOST_CONTEXT", "MEMORY", "ARTIFACT", "PLAN", "MODE_HINT")),

    ROLEPLAY(Set.of(
            "MODE_LOCK", "WORLD_STATE",
            "IDENTITY", "CHARACTER", "RELATIONSHIP",
            "GROUP_CONTEXT", "USER_PROFILE",
            "MEMORY", "ARTIFACT", "MODE_HINT")),

    GAME(Set.of(
            "MODE_LOCK", "WORLD_STATE",
            "IDENTITY", "CHARACTER",
            "ARTIFACT", "MEMORY",
            "MODE_HINT")),

    FULL(Set.of(
            "MODE_LOCK", "WORLD_STATE",
            "PERSONA", "IDENTITY", "CHARACTER", "RELATIONSHIP",
            "GROUP_CONTEXT", "USER_PROFILE",
            "WORKSPACE", "HOST_CONTEXT", "MEMORY", "ARTIFACT", "PLAN", "MODE_HINT"));

    private final Set<String> allowedLayers;

    private static final Logger log = LoggerFactory.getLogger(PromptPolicy.class);

    PromptPolicy(Set<String> allowedLayers) {
        this.allowedLayers = allowedLayers;
    }

    /**
     * 过滤 PromptLayer 列表，只保留当前策略允许的层。
     * 保留原有 priority 排序（由 Assembler 统一排序）。
     *
     * 强制保留层：BASE_SYSTEM 是 Agent 的核心行为准则，所有 Policy 都必须包含。
     */
    private static final Set<String> MANDATORY_LAYERS = Set.of("BASE_SYSTEM");

    public List<PromptLayer> filter(List<PromptLayer> layers) {
        if (layers == null || layers.isEmpty()) {
            return List.of();
        }
        return layers.stream()
                .filter(layer -> MANDATORY_LAYERS.contains(layer.name())
                        || allowedLayers.contains(layer.name()))
                .collect(Collectors.toList());
    }

    /**
     * 根据 Agent 名称推断合适的 PromptPolicy。
     * 如果无法匹配，返回 FULL（包含所有层）。
     */
    public static PromptPolicy forAgent(String agentName) {
        if (agentName == null) {
            return FULL;
        }
        String lower = agentName.toLowerCase();
        if (lower.contains("code") || lower.contains("代码")) {
            return CODE;
        }
        if (lower.contains("search") || lower.contains("搜索")) {
            return SEARCH;
        }
        if (lower.contains("roleplay") || lower.contains("角色")) {
            return ROLEPLAY;
        }
        if (lower.contains("chat") || lower.contains("对话")) {
            return CHAT;
        }
        return FULL;
    }

    /**
     * 根据用户请求内容推断合适的 PromptPolicy。
     * 用于在 Agent 选择之前确定 Prompt 组装策略。
     */
    public static PromptPolicy forRequest(String request) {
        if (request == null || request.isEmpty()) {
            log.info("[DIAG-PromptPolicy] → CHAT | reason=empty request");
            return CHAT;
        }
        String lower = request.toLowerCase();
        if (lower.contains("代码") || lower.contains("code") || lower.contains("编程")
                || lower.contains("bug") || lower.contains("重构") || lower.contains("refactor")
                || lower.contains("函数") || lower.contains("function")) {
            log.info("[DIAG-PromptPolicy] → CODE | reason=keyword match | request='{}'",
                    request.length() > 60 ? request.substring(0, 60) + "..." : request);
            return CODE;
        }
        if (lower.contains("搜索") || lower.contains("search") || lower.contains("查找")
                || lower.contains("查询") || lower.contains("最新") || lower.contains("新闻")) {
            log.info("[DIAG-PromptPolicy] → SEARCH | reason=keyword match | request='{}'",
                    request.length() > 60 ? request.substring(0, 60) + "..." : request);
            return SEARCH;
        }
        log.info("[DIAG-PromptPolicy] → CHAT | reason=default fallthrough | request='{}'",
                request.length() > 60 ? request.substring(0, 60) + "..." : request);
        return CHAT;
    }
}