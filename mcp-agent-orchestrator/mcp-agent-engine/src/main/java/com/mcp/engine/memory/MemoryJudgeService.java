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
 * 记忆判断器 - 让 LLM 判断一段对话是否值得进入长期记忆。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryJudgeService {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String JUDGE_PROMPT = """
        你是一个记忆判断器。你的任务是判断以下对话内容是否值得进入长期记忆。

        【记忆类型定义】
        - PROFILE: 用户身份、职业、技能等基本属性（如"我是软件工程师"）
        - PREFERENCE: 喜欢/不喜欢的事物（如"我喜欢喝冰可乐"）
        - HABIT: 经常重复的行为模式（如"每天早上跑步"）
        - GOAL: 长期目标（如"我想在年底前完成这个项目"）
        - PROJECT: 正在进行的项目（如"我在开发Terraria游戏"）
        - FACT: 可验证的客观事实（如"Java 21 发布了虚拟线程"）
        - RELATION: 人物关系（如"张三是我同事"）
        - TEMPORARY: 短期有效的信息（如"今天我去了超市"）
        - EVENT: 值得记住的重要事件（如"上周参加了技术大会"）

        【判断标准】
        - importance 0-100: 重要性评分
          - 用户身份/关系: 80-100
          - 长期偏好/目标: 70-90
          - 项目/事实: 50-80
          - 临时信息: 0-20
        - 如果内容不值得长期保存，设置 importance=0 并说明原因

        【输出格式】
        严格输出 JSON 数组，每个元素包含：
        {
            "content": "提取的记忆内容（简洁准确）",
            "memoryType": "PROFILE/PREFERENCE/HABIT/GOAL/PROJECT/FACT/RELATION/TEMPORARY/EVENT",
            "importance": 0-100,
            "reason": "判断理由",
            "shouldSave": true/false
        }

        对话内容：
        %s

        请输出 JSON（不要包含 markdown 标记）：
        """;

    public Mono<List<MemoryCandidate>> judge(String conversation) {
        if (conversation == null || conversation.isBlank()) {
            return Mono.just(List.of());
        }

        String prompt = JUDGE_PROMPT.formatted(conversation);

        return llmClient.generate(prompt)
                .map(this::parseCandidates)
                .doOnNext(candidates -> log.info("[MemoryJudge] 从 {} 字符对话中提取了 {} 条候选记忆",
                        conversation.length(), candidates.size()))
                .onErrorReturn(List.of());
    }

    private List<MemoryCandidate> parseCandidates(String llmResponse) {
        try {
            String json = extractJson(llmResponse);
            List<Map<String, Object>> raw = objectMapper.readValue(
                    json, new TypeReference<List<Map<String, Object>>>() {});

            return raw.stream()
                    .filter(m -> Boolean.TRUE.equals(m.get("shouldSave")))
                    .map(m -> new MemoryCandidate(
                            (String) m.get("content"),
                            MemoryType.valueOf((String) m.get("memoryType")),
                            ((Number) m.get("importance")).intValue(),
                            (String) m.get("reason")
                    ))
                    .toList();
        } catch (Exception e) {
            log.error("[MemoryJudge] 解析 LLM 响应失败: {}", e.getMessage());
            return List.of();
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

    public record MemoryCandidate(
            String content,
            MemoryType memoryType,
            int importance,
            String reason
    ) {}
}