package com.mcp.engine.reflection;

import com.mcp.common.channel.RoleRuntime;
import com.mcp.llm.client.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * LLM 反射裁判 — 语义判断 Agent 输出是否违反角色锁。
 * 替代 Regex 方案，用 LLM 做语义级别的违规检测。
 * 检测到违规时生成 Reflection Prompt，由上层进行重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReflectionJudge {

    private final LlmClient llmClient;

    private static final int MAX_RETRY = 2;

    private static final String JUDGE_SYSTEM_PROMPT = """
            【角色锁校验器】
            你的唯一任务是判断以下 Agent 输出是否违反了角色扮演规则。

            违规标准（满足任一即判定 FAIL）：
            1. Agent 跳出角色，以 AI / 助手 / 系统身份说话
            2. Agent 安慰用户、进行心理咨询、询问用户真实状态
            3. Agent 使用"深呼吸"、"恢复秩序"、"告诉我希望我怎么做"等跳出角色语言
            4. Agent 讨论"现实世界"、"这是游戏"、"我们回到现在"、"暂停一下"
            5. Agent 称呼用户为"Master"（指跳出角色的称呼，角色设定内的除外）
            6. Agent 以保护者/陪伴者身份对用户进行情绪安抚

            只回答一个词: PASS 或 FAIL。不要有任何其他内容。
            """;

    /**
     * 语义裁判：判断 Agent 输出是否违反角色锁。
     */
    public Mono<JudgeResult> judge(String agentOutput, RoleRuntime roleRuntime) {
        if (agentOutput == null || agentOutput.isBlank()) {
            return Mono.just(JudgeResult.PASS);
        }
        if (roleRuntime == null) {
            return Mono.just(JudgeResult.PASS);
        }

        String roleContext = buildRoleContext(roleRuntime);
        String userPrompt = roleContext + "\n待校验输出：\n" + agentOutput;

        return llmClient.generateWithSystemPrompt(JUDGE_SYSTEM_PROMPT, userPrompt)
                .map(raw -> {
                    String trimmed = raw.trim().toUpperCase();
                    boolean failed = trimmed.contains("FAIL");
                    if (failed) {
                        log.warn("[ReflectionJudge] Role lock violation detected. Output preview: {}",
                                truncate(agentOutput, 80));
                    }
                    return new JudgeResult(!failed, failed ? "角色锁违规" : null);
                })
                .onErrorResume(e -> {
                    log.warn("[ReflectionJudge] Judge LLM call failed, defaulting to PASS: {}", e.getMessage());
                    return Mono.just(JudgeResult.PASS);
                })
                .defaultIfEmpty(JudgeResult.PASS);
    }

    /**
     * 构建角色上下文，帮助裁判理解当前角色设定。
     */
    private String buildRoleContext(RoleRuntime roleRuntime) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前角色模式：").append(roleRuntime.getMode() != null ? roleRuntime.getMode().name() : "UNKNOWN").append("\n");
        if (roleRuntime.getRoleName() != null) {
            sb.append("角色名：").append(roleRuntime.getRoleName()).append("\n");
        }
        if (roleRuntime.getRoleDescription() != null) {
            sb.append("角色描述：").append(roleRuntime.getRoleDescription()).append("\n");
        }
        if (roleRuntime.getWorld() != null) {
            sb.append("世界观：").append(roleRuntime.getWorld()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 生成 Reflection Prompt，用于重试时注入到 systemPrompt 中。
     */
    public String buildReflectionPrompt(String originalResponse) {
        return """
                【系统警告 - 角色锁违规 - 请重新生成】
                你上一次的回应违反了角色扮演模式规则。

                违规的原始回应：
                "%s"

                请重新生成回应，严格遵守以下规则：
                1. 你必须以角色身份回应，不能跳出角色
                2. 禁止心理咨询 / 安慰用户 / 情绪安抚
                3. 禁止称呼用户为"Master"（除非角色设定如此）
                4. 禁止讨论"现实世界"或"这是游戏"
                5. 禁止使用"深呼吸"、"恢复秩序"、"暂停一下"等跳出角色语言
                6. 保持角色沉浸感，以角色方式应对当前情境
                """.formatted(truncate(originalResponse, 300));
    }

    public int getMaxRetry() {
        return MAX_RETRY;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "null";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    public record JudgeResult(boolean passed, String reason) {
        public static final JudgeResult PASS = new JudgeResult(true, null);
    }
}