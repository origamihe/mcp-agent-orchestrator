package com.mcp.engine.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.core.domain.memory.MemoryType;
import com.mcp.llm.client.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

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

        String prompt = EXTRACT_PROMPT.formatted(conversation);

        return llmClient.generate(prompt)
                .map(this::parseCandidates)
                .doOnNext(candidates -> log.info("[MemoryExtractor] 从 {} 字符对话中抽取了 {} 条候选记忆",
                        conversation.length(), candidates.size()))
                .onErrorReturn(List.of());
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
        if (trimmed.startsWith("```json")) {
            int start = trimmed.indexOf("[");
            int end = trimmed.lastIndexOf("]");
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        int start = trimmed.indexOf("[");
        int end = trimmed.lastIndexOf("]");
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return "[]";
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