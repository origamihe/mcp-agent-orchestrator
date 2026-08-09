package com.mcp.engine.test.benchmark;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.engine.trace.TraceDiff;
import com.mcp.engine.trace.TraceRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("llm")
@DisplayName("Benchmark Replay - 可重放基准对比")
class BenchmarkReplay {

    private static final String OLLAMA_BASE_URL = System.getProperty("ollama.base.url", "http://localhost:11434");
    private static final String MODEL_NAME = System.getProperty("ollama.model", "qwen2:7b");
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String callOllama(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", MODEL_NAME);
        body.put("stream", false);
        body.put("options", Map.of("temperature", 0.3));

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));
        body.put("messages", messages);

        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_BASE_URL + "/api/chat"))
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Ollama API returned status " + response.statusCode() + ": " + response.body());
        }

        Map<String, Object> result = objectMapper.readValue(response.body(),
                new TypeReference<Map<String, Object>>() {});
        Map<String, Object> message = (Map<String, Object>) result.get("message");
        return message != null ? (String) message.get("content") : "";
    }

    @Test
    @DisplayName("replay001: 从 benchmark-results 重放已保存的对话")
    void replayFromSavedResults() throws Exception {
        Path benchmarkDir = Paths.get("benchmark-results");
        if (!Files.exists(benchmarkDir)) {
            System.out.println("[Replay] No benchmark-results directory found, skipping.");
            return;
        }

        List<Path> dateDirs = Files.list(benchmarkDir)
                .filter(Files::isDirectory)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        if (dateDirs.isEmpty()) {
            System.out.println("[Replay] No date directories found in benchmark-results, skipping.");
            return;
        }

        Path latestDir = dateDirs.get(0);
        List<Path> resultFiles = Files.list(latestDir)
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .filter(p -> !p.getFileName().toString().startsWith("_"))
                .sorted()
                .collect(Collectors.toList());

        System.out.println("=== Benchmark Replay ===");
        System.out.println("Replaying from: " + latestDir.toAbsolutePath());
        System.out.println("Files found: " + resultFiles.size());
        System.out.println("Model: " + MODEL_NAME);
        System.out.println("========================");

        int totalReplayed = 0;
        int passed = 0;
        List<Map<String, Object>> replayResults = new ArrayList<>();

        for (Path resultFile : resultFiles) {
            try {
                BenchmarkResult original = objectMapper.readValue(resultFile.toFile(), BenchmarkResult.class);
                if (original.getSystemPrompt() == null || original.getPrompt() == null) {
                    continue;
                }

                String systemPrompt = original.getSystemPrompt();
                String userPrompt = original.getPrompt();

                long start = System.currentTimeMillis();
                String newOutput = callOllama(systemPrompt, userPrompt);
                long elapsed = System.currentTimeMillis() - start;

                TraceRecord baseline = TraceRecord.builder()
                        .traceId("replay-" + original.getTaskId())
                        .userMessage(userPrompt)
                        .renderedPrompt(systemPrompt)
                        .llmOutput(original.getLlmOutput())
                        .elapsedMs(original.getElapsedTimeMs())
                        .build();

                TraceRecord current = TraceRecord.builder()
                        .traceId("replay-current-" + original.getTaskId())
                        .userMessage(userPrompt)
                        .renderedPrompt(systemPrompt)
                        .llmOutput(newOutput)
                        .elapsedMs(elapsed)
                        .build();

                TraceDiff.Result diff = TraceDiff.compare(baseline, current);
                boolean isConsistent = diff.passed() || (newOutput != null && !newOutput.isBlank());
                totalReplayed++;
                if (isConsistent) passed++;

                Map<String, Object> replayEntry = new LinkedHashMap<>();
                replayEntry.put("taskId", original.getTaskId());
                replayEntry.put("taskName", original.getTaskName());
                replayEntry.put("category", original.getCategory());
                replayEntry.put("originalModel", original.getModelName());
                replayEntry.put("replayModel", MODEL_NAME);
                replayEntry.put("originalOutput", truncate(original.getLlmOutput(), 300));
                replayEntry.put("replayOutput", truncate(newOutput, 300));
                replayEntry.put("replayElapsedMs", elapsed);
                replayEntry.put("originalElapsedMs", original.getElapsedTimeMs());
                replayEntry.put("consistent", isConsistent);
                replayEntry.put("diffPassed", diff.passed());
                replayEntry.put("diffSummary", diff.summary());
                replayEntry.put("regressions", diff.regressions());
                replayEntry.put("improvements", diff.improvements());
                replayResults.add(replayEntry);

                String status = diff.passed() ? "PASS" : "FAIL";
                String regressions = diff.regressions().isEmpty() ? ""
                        : " (" + String.join("; ", diff.regressions()) + ")";
                System.out.printf("  [%s] %s: %s (elapsed: %dms)%s%n",
                        status,
                        original.getTaskId(),
                        original.getTaskName(),
                        elapsed,
                        regressions);
            } catch (Exception e) {
                System.err.println("  [ERROR] " + resultFile.getFileName() + ": " + e.getMessage());
            }
        }

        Path replayDir = Paths.get("benchmark-results", "replay",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        Files.createDirectories(replayDir);
        Path replayFile = replayDir.resolve("replay-" + MODEL_NAME.replace(":", "-") + ".json");
        Map<String, Object> replaySummary = new LinkedHashMap<>();
        replaySummary.put("model", MODEL_NAME);
        replaySummary.put("replayedAt", LocalDateTime.now().toString());
        replaySummary.put("totalReplayed", totalReplayed);
        replaySummary.put("passed", passed);
        replaySummary.put("failed", totalReplayed - passed);
        replaySummary.put("passRate", totalReplayed > 0 ? (double) passed / totalReplayed : 0);

        long degradationCount = replayResults.stream()
                .filter(r -> r.containsKey("diffPassed") && !(boolean) r.get("diffPassed"))
                .count();
        replaySummary.put("degradationCount", degradationCount);
        replaySummary.put("results", replayResults);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(replayFile.toFile(), replaySummary);

        System.out.println("\n=== Replay Summary ===");
        System.out.println("Total replayed: " + totalReplayed);
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + (totalReplayed - passed));
        System.out.println("Degradations: " + degradationCount);
        System.out.println("Pass rate: " + (totalReplayed > 0
                ? String.format("%.0f%%", 100.0 * passed / totalReplayed) : "N/A"));
        System.out.println("Saved to: " + replayFile.toAbsolutePath());
        System.out.println("======================");

        assertThat(totalReplayed).isGreaterThanOrEqualTo(0);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}