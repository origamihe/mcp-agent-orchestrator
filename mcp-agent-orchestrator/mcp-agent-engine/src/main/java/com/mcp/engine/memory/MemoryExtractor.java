package com.mcp.engine.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.core.domain.memory.MemoryType;
import com.mcp.llm.client.ChatMessage;
import com.mcp.llm.client.MessageType;
import com.mcp.llm.client.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 记忆抽取器 - 从对话中提取结构化记忆条目。
 *
 * 核心职责：将非结构化的对话文本转化为结构化的记忆候选。
 * 与 MemoryJudgeService 的区别：
 *   - Judge 只判断"要不要存"（二元决策）
 *   - Extractor 抽取"存什么"（结构化信息）
 *
 * 抽取类别：
 *   PREFERENCE, IDENTITY, GOAL, PROJECT, FACT,
 *   RELATION, HABIT, SKILL, SCHEDULE, EVENT, TEMPORARY
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryExtractor {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 需要过滤的文件内容标记。
     * 预加载的文件内容和 Workspace 中已打开的文件内容属于临时上下文，
     * 不应被抽取为长期记忆。否则会导致"蚁王激光枪""沙暴炸弹"等文件内容
     * 污染长期记忆存储。
     */
    private static final List<Pattern> FILE_CONTENT_BLOCK_PATTERNS = List.of(
            Pattern.compile("【已预加载的文件内容】"),
            Pattern.compile("【已打开文件内容（自动恢复）】"),
            Pattern.compile("【已打开文件内容】"),
            Pattern.compile("--- 文件:.*---"),
            Pattern.compile("以下文件内容由系统自动读取并注入上下文")
    );

    /**
     * 需要过滤的规划/模板泄露模式。
     * 这些模式通常出现在 Assistant 的消息中，是 Prompt 模板泄露或工具调用前的预告文字，
     * 不是真实的知识或对话内容，不应被抽取为记忆。
     */
    private static final List<Pattern> PLANNING_FILTER_PATTERNS = List.of(
            Pattern.compile("我会使用.*工具"),
            Pattern.compile("接下来我将.*"),
            Pattern.compile("请稍等.*"),
            Pattern.compile("以下是.*预览结构"),
            Pattern.compile("以下是.*搜索结果"),
            Pattern.compile("第[一二三四五12345]步[：:].*"),
            Pattern.compile("【核心发现】"),
            Pattern.compile("【主要观点】"),
            Pattern.compile("【争议分析】"),
            Pattern.compile("【不确定性说明】"),
            Pattern.compile("【综合判断】"),
            Pattern.compile("【研究流程规则】"),
            Pattern.compile("【内部规则】"),
            Pattern.compile("【研究方法】"),
            Pattern.compile("【执行计划】"),
            Pattern.compile("我先.*搜索"),
            Pattern.compile("我先.*查找"),
            Pattern.compile("让我.*搜索"),
            Pattern.compile("正在.*搜索"),
            Pattern.compile("开始.*收集"),
            Pattern.compile("完成后.*返回"),
            Pattern.compile("tool.*call", Pattern.CASE_INSENSITIVE),
            Pattern.compile("function.*call", Pattern.CASE_INSENSITIVE),
            Pattern.compile("搜索结果预览"),
            Pattern.compile("搜索计划"),
            Pattern.compile("执行步骤"),
            Pattern.compile("工具调用"),
            Pattern.compile("信息来源"),
            Pattern.compile("引用所有来源"),
            Pattern.compile("按主题整合"),
            Pattern.compile("严禁向用户输出")
    );

    private static final String EXTRACT_PROMPT = """
        你是一个记忆抽取器。你的任务是从对话中提取所有值得长期保存的结构化信息。

        【可抽取的记忆类型】
        - PREFERENCE: 用户偏好（喜欢/不喜欢/想要/不想要的事物、风格、方式）
          例："我喜欢用简洁的代码风格" → PREFERENCE
        - IDENTITY: 用户身份信息（姓名、职业、技能、角色、称呼偏好）
          例："我是软件工程师" / "以后叫我主人" → IDENTITY
        - GOAL: 长期目标或短期目标
          例："我想在年底前完成这个项目" → GOAL
        - PROJECT: 正在进行的项目/工作
          例："我在开发一个Terraria游戏" → PROJECT
        - FACT: 可验证的客观事实
          例："Java 21 发布了虚拟线程" → FACT
        - RELATION: 人物关系
          例："张三是我同事" → RELATION
        - HABIT: 经常重复的行为模式
          例："每天早上跑步" → HABIT
        - SKILL: 用户具备的技能或能力
          例："我会用Python做数据分析" → SKILL
        - SCHEDULE: 日程安排
          例："下周一到周三出差" → SCHEDULE
        - EVENT: 值得记住的重要事件
          例："上周参加了技术大会" → EVENT
        - TEMPORARY: 短期有效的信息（今天吃了什么等）
          例："今天吃了拉面" → TEMPORARY

        【抽取规则】
        1. 每条记忆内容必须简洁、准确、可独立理解
        2. 必须引用原文作为来源（sourceQuote）
        3. 如果对话中没有值得抽取的信息，返回空数组
        4. 称呼偏好（如"叫我XX"）必须抽取为 IDENTITY 类型
        5. 临时信息（吃的什么、天气等）也要抽取但标记为 TEMPORARY

        【禁止抽取的内容 — 非常重要！】
        6. 不要抽取助手的执行计划、工具调用过程（如"我会使用deep_research..."）
        7. 不要抽取回复模板框架（如"【核心发现】...【争议分析】..."）
        8. 不要抽取工具返回的原始数据（如搜索结果JSON）
        9. 不要抽取系统提示词或内部规则
        10. 只抽取用户真实信息、对话中产生的客观事实和结论
        11. 不要抽取被预加载的文件内容（如文件中的物品名称、道具列表、配置项等），这些是临时文件上下文，不是长期记忆

        【输出格式】
        严格输出 JSON 数组：
        [
            {
                "content": "记忆内容（简洁准确）",
                "memoryType": "PREFERENCE/IDENTITY/GOAL/PROJECT/FACT/RELATION/HABIT/SKILL/SCHEDULE/EVENT/TEMPORARY",
                "sourceQuote": "原文引用",
                "confidence": 0-100
            }
        ]

        对话内容：
        %s

        请输出 JSON（不要包含 markdown 标记）：
        """;

    public Mono<List<MemoryCandidate>> extract(String conversation) {
        if (conversation == null || conversation.isBlank()) {
            return Mono.just(List.of());
        }

        String filtered = filterPlanningMessages(conversation);
        int filteredLen = conversation.length() - filtered.length();
        if (filteredLen > 0) {
            log.info("[MemoryExtractor] 过滤了 {} 字符的规划/模板泄露内容", filteredLen);
        }

        String fileFiltered = filterFileContentBlocks(filtered);
        int fileFilteredLen = filtered.length() - fileFiltered.length();
        if (fileFilteredLen > 0) {
            log.info("[MemoryExtractor] 过滤了 {} 字符的文件内容（不会进入长期记忆）", fileFilteredLen);
        }

        if (fileFiltered.isBlank()) {
            log.info("[MemoryExtractor] 过滤后对话为空，跳过记忆抽取");
            return Mono.just(List.of());
        }

        String prompt = EXTRACT_PROMPT.formatted(fileFiltered);

        return llmClient.generate(prompt)
                .map(this::parseCandidates)
                .doOnNext(candidates -> log.info("[MemoryExtractor] 从 {} 字符对话中抽取了 {} 条候选记忆",
                        fileFiltered.length(), candidates.size()))
                .onErrorReturn(List.of());
    }

    /**
     * 基于消息元数据（MessageType）过滤并提取记忆。
     * 这是推荐的方式：Memory 只学习 NORMAL 和 SUMMARY 类型的消息，
     * SYSTEM、PLAN、TOOL、TEMPLATE 类型的消息在 Java 层就被过滤掉。
     * 配合 LLM Prompt 中的禁止抽取规则，形成 Java + LLM 双层防护。
     */
    public Mono<List<MemoryCandidate>> extractWithMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Mono.just(List.of());
        }

        List<ChatMessage> eligible = filterByMessageType(messages);

        if (eligible.isEmpty()) {
            log.info("[MemoryExtractor] 消息类型过滤后无有效消息（共{}条消息），跳过记忆抽取",
                    messages.size());
            return Mono.just(List.of());
        }

        String conversation = buildConversationText(eligible);

        log.info("[MemoryExtractor] 消息类型过滤: 总{}条 → 有效{}条 (NORMAL/SUMMARY), 文本长度={}",
                messages.size(), eligible.size(), conversation.length());

        String filtered = filterFileContentBlocks(conversation);
        int filteredLen = conversation.length() - filtered.length();
        if (filteredLen > 0) {
            log.info("[MemoryExtractor] 过滤了 {} 字符的文件内容（不会进入长期记忆）", filteredLen);
        }

        if (filtered.isBlank()) {
            log.info("[MemoryExtractor] 过滤后对话为空，跳过记忆抽取");
            return Mono.just(List.of());
        }

        String prompt = EXTRACT_PROMPT.formatted(filtered);

        return llmClient.generate(prompt)
                .map(this::parseCandidates)
                .doOnNext(candidates -> log.info("[MemoryExtractor] 从 {} 条消息中抽取了 {} 条候选记忆",
                        eligible.size(), candidates.size()))
                .onErrorReturn(List.of());
    }

    /**
     * 按 MessageType 过滤：只保留 NORMAL 和 SUMMARY 类型的消息。
     * SYSTEM、PLAN、TOOL、TEMPLATE 全部丢弃。
     */
    private List<ChatMessage> filterByMessageType(List<ChatMessage> messages) {
        return messages.stream()
                .filter(m -> {
                    MessageType type = m.getMessageType() != null ? m.getMessageType() : MessageType.NORMAL;
                    return type == MessageType.NORMAL || type == MessageType.SUMMARY;
                })
                .toList();
    }

    /**
     * 将过滤后的消息列表拼接为对话文本。
     * 保留角色信息，帮助 LLM 理解上下文。
     */
    private String buildConversationText(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            String role = msg.getRole();
            if (role == null) role = "unknown";
            String content = msg.getContent();
            if (content == null || content.isBlank()) continue;

            switch (role) {
                case "user" -> sb.append("用户: ").append(content).append("\n");
                case "assistant" -> sb.append("助手: ").append(content).append("\n");
                default -> sb.append(content).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * 过滤对话中的文件内容块。
     * 预加载的文件内容和 Workspace 中已打开的文件内容属于临时上下文，
     * 不应被抽取为长期记忆。例如用户读取了一个 prompt.txt 文件，
     * 其中的"蚁王激光枪""沙暴炸弹"等是文件内容，而非用户的项目信息。
     * 此方法将整个文件内容块从对话中移除后再进行记忆抽取。
     */
    private String filterFileContentBlocks(String conversation) {
        if (conversation == null || conversation.isBlank()) {
            return conversation;
        }

        boolean hasFileContent = false;
        for (Pattern pattern : FILE_CONTENT_BLOCK_PATTERNS) {
            if (pattern.matcher(conversation).find()) {
                hasFileContent = true;
                break;
            }
        }

        if (!hasFileContent) {
            return conversation;
        }

        String[] lines = conversation.split("\n");
        StringBuilder result = new StringBuilder();
        boolean inFileBlock = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (!inFileBlock) {
                    result.append(line).append("\n");
                }
                continue;
            }

            boolean matchesFileBlock = false;
            for (Pattern pattern : FILE_CONTENT_BLOCK_PATTERNS) {
                if (pattern.matcher(trimmed).find()) {
                    matchesFileBlock = true;
                    break;
                }
            }

            if (matchesFileBlock) {
                inFileBlock = true;
                continue;
            }

            if (inFileBlock && !trimmed.startsWith("-") && !trimmed.startsWith("  ")
                    && !trimmed.startsWith("---") && !trimmed.startsWith("---")
                    && !trimmed.startsWith("(")) {
                inFileBlock = false;
            }

            if (!inFileBlock) {
                result.append(line).append("\n");
            }
        }

        return result.toString().trim();
    }

    /**
     * 过滤对话中的规划/模板泄露内容。
     * 将包含规划模式的行从对话中移除，防止 Memory 学习到 Prompt 模板而非真实知识。
     */
    private String filterPlanningMessages(String conversation) {
        String[] lines = conversation.split("\n");
        StringBuilder result = new StringBuilder();
        boolean inFilteredBlock = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (!inFilteredBlock) {
                    result.append(line).append("\n");
                }
                continue;
            }

            boolean matchesFilter = false;
            for (Pattern pattern : PLANNING_FILTER_PATTERNS) {
                if (pattern.matcher(trimmed).find()) {
                    matchesFilter = true;
                    break;
                }
            }

            if (matchesFilter) {
                inFilteredBlock = true;
                log.debug("[MemoryExtractor] 过滤行: {}", trimmed.length() > 80
                        ? trimmed.substring(0, 80) + "..." : trimmed);
                continue;
            }

            if (inFilteredBlock && !trimmed.startsWith("-") && !trimmed.startsWith("  ")) {
                inFilteredBlock = false;
            }

            if (!inFilteredBlock) {
                result.append(line).append("\n");
            }
        }

        return result.toString().trim();
    }

    private List<MemoryCandidate> parseCandidates(String llmResponse) {
        try {
            String json = extractJson(llmResponse);
            List<Map<String, Object>> raw = objectMapper.readValue(
                    json, new TypeReference<List<Map<String, Object>>>() {});

            return raw.stream()
                    .map(m -> new MemoryCandidate(
                            (String) m.get("content"),
                            safeValueOf((String) m.get("memoryType")),
                            ((Number) m.getOrDefault("confidence", 50)).intValue(),
                            (String) m.getOrDefault("sourceQuote", "")
                    ))
                    .toList();
        } catch (Exception e) {
            log.error("[MemoryExtractor] 解析 LLM 响应失败: {}", e.getMessage());
            return List.of();
        }
    }

    private MemoryType safeValueOf(String type) {
        try {
            return type != null ? MemoryType.valueOf(type) : MemoryType.TEMPORARY;
        } catch (IllegalArgumentException e) {
            log.warn("[MemoryExtractor] 未知记忆类型: {}, 默认使用 TEMPORARY", type);
            return MemoryType.TEMPORARY;
        }
    }

    private String extractJson(String response) {
        String trimmed = response.trim();
        log.debug("[MemoryExtractor] extractJson input (first 300 chars): {}",
                trimmed.length() > 300 ? trimmed.substring(0, 300) + "..." : trimmed);

        // 1. 去掉 markdown 代码块标记
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring("```json".length()).trim();
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring("```".length()).trim();
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }

        // 2. 找到第一个 '[' 和匹配的最后一个 ']'（使用括号计数，避免 markdown 注释干扰）
        int firstBracket = trimmed.indexOf('[');
        if (firstBracket < 0) {
            log.warn("[MemoryExtractor] extractJson: no '[' found in response");
            return "[]";
        }

        int depth = 0;
        int lastBracket = -1;
        for (int i = firstBracket; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    lastBracket = i;
                    break;
                }
            }
        }

        if (lastBracket < 0) {
            log.warn("[MemoryExtractor] extractJson: no matching ']' found");
            return "[]";
        }

        String jsonCandidate = trimmed.substring(firstBracket, lastBracket + 1);

        // 3. 验证提取的 JSON 是否合法，不合法则尝试修复
        try {
            objectMapper.readTree(jsonCandidate);
            return jsonCandidate;
        } catch (Exception firstAttempt) {
            log.debug("[MemoryExtractor] extractJson: first attempt failed: {}", firstAttempt.getMessage());

            // 4. 尝试移除 JSON 数组内部的行内注释（# 开头的行）
            String cleaned = cleanupJsonArray(jsonCandidate);
            try {
                objectMapper.readTree(cleaned);
                log.info("[MemoryExtractor] extractJson: cleaned JSON successfully ({} → {} chars)",
                        jsonCandidate.length(), cleaned.length());
                return cleaned;
            } catch (Exception secondAttempt) {
                log.warn("[MemoryExtractor] extractJson: cleanup also failed: {}", secondAttempt.getMessage());
                // 5. 最后尝试：找到第一个 ']' 之后的合法 JSON 截断点
                String truncated = tryTruncateToValidJson(jsonCandidate);
                try {
                    objectMapper.readTree(truncated);
                    log.info("[MemoryExtractor] extractJson: truncated JSON successfully ({} → {} chars)",
                            jsonCandidate.length(), truncated.length());
                    return truncated;
                } catch (Exception thirdAttempt) {
                    log.error("[MemoryExtractor] extractJson: all attempts failed, returning []. LLM raw response (first 500 chars): {}",
                            response.length() > 500 ? response.substring(0, 500) + "..." : response);
                    return "[]";
                }
            }
        }
    }

    /**
     * 移除 JSON 数组内部的行内注释（以 # 开头的行）和尾随的非 JSON 内容。
     */
    private String cleanupJsonArray(String jsonCandidate) {
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < jsonCandidate.length(); i++) {
            char c = jsonCandidate.charAt(i);

            if (escaped) {
                sb.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\') {
                sb.append(c);
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                sb.append(c);
                continue;
            }

            if (!inString && c == '#') {
                // 跳过从 # 到行尾的所有字符
                while (i < jsonCandidate.length() && jsonCandidate.charAt(i) != '\n') {
                    i++;
                }
                if (i < jsonCandidate.length()) {
                    sb.append('\n');
                }
                continue;
            }

            sb.append(c);
        }

        return sb.toString().trim();
    }

    /**
     * 尝试通过截断到最后一个合法 JSON token 来修复 JSON。
     */
    private String tryTruncateToValidJson(String jsonCandidate) {
        // 找到最后一个 '}' 或 ']'，尝试截断
        int lastCloseBrace = jsonCandidate.lastIndexOf('}');
        int lastCloseBracket = jsonCandidate.lastIndexOf(']');

        if (lastCloseBracket > 0) {
            String candidate = jsonCandidate.substring(0, lastCloseBracket + 1);
            try {
                objectMapper.readTree(candidate);
                return candidate;
            } catch (Exception ignored) {
            }
        }

        if (lastCloseBrace > 0) {
            String candidate = jsonCandidate.substring(0, lastCloseBrace + 1);
            try {
                objectMapper.readTree(candidate);
                return candidate;
            } catch (Exception ignored) {
            }
        }

        return jsonCandidate;
    }

    /**
     * 记忆候选 - 抽取后的结构化记忆条目
     */
    public record MemoryCandidate(
            String content,
            MemoryType memoryType,
            int confidence,
            String sourceQuote
    ) {}
}