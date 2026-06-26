package com.mcp.engine.reflection;

import com.mcp.core.domain.memory.FailureEntity;
import com.mcp.core.repository.FailureLibraryRepository;
import com.mcp.llm.client.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FailureLibraryService {

    private final FailureLibraryRepository repository;
    private final LlmClient llmClient;

    private static final String FAILURE_MATCH_PROMPT = """
        你是一个失败模式匹配器。判断当前任务是否与已知失败模式匹配。

        【已知失败模式】
        %s

        【当前任务】
        用户请求: %s
        执行过程: %s
        错误信息: %s

        【输出格式】
        严格输出 JSON：
        {
            "matched": true/false,
            "matchedFailureId": 匹配的 Failure ID（如果 matched=true），
            "shouldWarn": true/false,
            "warningMessage": "警告信息（如果 shouldWarn=true）"
        }
        """;

    public FailureEntity create(FailureEntity failure) {
        FailureEntity saved = repository.save(failure);
        log.info("[FailureLibrary] 记录 Failure: taskPattern='{}', error='{}'",
                saved.getTaskPattern(), saved.getErrorSignature());
        return saved;
    }

    public FailureEntity createOrUpdate(String taskPattern, String errorSignature,
                                         String rootCause, String correctApproach,
                                         String contextSnapshot) {
        List<FailureEntity> existing = repository.findUnresolvedByTaskPattern(taskPattern);

        for (FailureEntity f : existing) {
            if (f.getErrorSignature() != null
                    && f.getErrorSignature().equalsIgnoreCase(errorSignature)) {
                repository.incrementOccurrence(f.getId());
                f.recordOccurrence();
                log.info("[FailureLibrary] Failure 重复出现 (第{}次): taskPattern='{}'",
                        f.getOccurrenceCount(), f.getTaskPattern());
                return f;
            }
        }

        FailureEntity newFailure = FailureEntity.builder()
                .taskPattern(taskPattern)
                .errorSignature(errorSignature)
                .rootCause(rootCause)
                .correctApproach(correctApproach)
                .contextSnapshot(contextSnapshot)
                .occurrenceCount(1)
                .isResolved(false)
                .build();
        return repository.save(newFailure);
    }

    public void markResolved(Long failureId, Long skillId) {
        repository.markResolved(failureId, skillId);
        log.info("[FailureLibrary] Failure {} 已由 Skill {} 解决", failureId, skillId);
    }

    public Mono<FailureMatchResult> matchFailure(String userRequest, String agentExecution,
                                                  String errorMessage) {
        List<FailureEntity> unresolvedFailures = repository.findByIsResolvedFalseOrderByOccurrenceCountDesc();

        if (unresolvedFailures.isEmpty()) {
            return Mono.just(FailureMatchResult.noMatch());
        }

        if (errorMessage != null && !errorMessage.isEmpty()) {
            for (FailureEntity f : unresolvedFailures) {
                if (f.getErrorSignature() != null
                        && errorMessage.toLowerCase().contains(f.getErrorSignature().toLowerCase())) {
                    log.info("[FailureLibrary] 签名匹配: taskPattern='{}', error='{}'",
                            f.getTaskPattern(), f.getErrorSignature());
                    return Mono.just(FailureMatchResult.matched(f));
                }
            }
        }

        String failuresText = unresolvedFailures.stream()
                .limit(10)
                .map(f -> String.format("[ID:%d] taskPattern='%s', error='%s', correct='%s'",
                        f.getId(), f.getTaskPattern(), f.getErrorSignature(), f.getCorrectApproach()))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        String prompt = FAILURE_MATCH_PROMPT.formatted(
                failuresText, userRequest, agentExecution,
                errorMessage != null ? errorMessage : "无");

        return llmClient.generate(prompt)
                .subscribeOn(Schedulers.boundedElastic())
                .map(response -> parseFailureMatch(response, unresolvedFailures))
                .onErrorReturn(FailureMatchResult.noMatch());
    }

    public String buildFailureWarning(List<FailureEntity> matchedFailures) {
        if (matchedFailures == null || matchedFailures.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 已知失败模式警告 (Failure Library)\n");
        sb.append("以下操作历史上有失败记录，请避免重复犯错：\n\n");

        for (int i = 0; i < matchedFailures.size(); i++) {
            FailureEntity f = matchedFailures.get(i);
            sb.append("### 失败 ").append(i + 1).append(": ").append(f.getTaskPattern());
            sb.append(" (发生 ").append(f.getOccurrenceCount()).append(" 次)\n");
            sb.append("- 错误: ").append(f.getErrorSignature()).append("\n");
            sb.append("- 根因: ").append(f.getRootCause()).append("\n");
            sb.append("- 正确做法: ").append(f.getCorrectApproach()).append("\n\n");
        }

        return sb.toString();
    }

    public List<FailureEntity> getUnresolvedFailures() {
        return repository.findByIsResolvedFalseOrderByOccurrenceCountDesc();
    }

    private FailureMatchResult parseFailureMatch(String response,
                                                  List<FailureEntity> failures) {
        try {
            String json = extractJson(response);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(json);

            boolean matched = node.has("matched") && node.get("matched").asBoolean();
            if (!matched) {
                return FailureMatchResult.noMatch();
            }

            long failureId = node.has("matchedFailureId") ? node.get("matchedFailureId").asLong() : -1;
            FailureEntity matchedFailure = failures.stream()
                    .filter(f -> f.getId().equals(failureId))
                    .findFirst()
                    .orElse(null);

            String warning = node.has("warningMessage") ? node.get("warningMessage").asText() : "";
            return FailureMatchResult.matchedWithWarning(matchedFailure, warning);
        } catch (Exception e) {
            log.debug("[FailureLibrary] 解析匹配结果失败: {}", e.getMessage());
            return FailureMatchResult.noMatch();
        }
    }

    private String extractJson(String response) {
        String trimmed = response.trim();
        int start = trimmed.indexOf("{");
        int end = trimmed.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return "{}";
    }

    public record FailureMatchResult(
            boolean matched,
            FailureEntity failure,
            String warningMessage
    ) {
        public static FailureMatchResult noMatch() {
            return new FailureMatchResult(false, null, "");
        }

        public static FailureMatchResult matched(FailureEntity failure) {
            return new FailureMatchResult(true, failure,
                    "警告: 任务 '" + failure.getTaskPattern()
                    + "' 历史失败 " + failure.getOccurrenceCount() + " 次。"
                    + " 正确做法: " + failure.getCorrectApproach());
        }

        public static FailureMatchResult matchedWithWarning(FailureEntity failure, String warning) {
            return new FailureMatchResult(true, failure, warning);
        }

        public boolean shouldWarn() {
            return matched && failure != null;
        }
    }
}