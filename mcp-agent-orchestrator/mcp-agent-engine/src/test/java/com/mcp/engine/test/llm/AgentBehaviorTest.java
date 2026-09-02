package com.mcp.engine.test.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("llm")
@DisplayName("Agent Behavior Tests - LLM 行为验证")
@SuppressWarnings("unchecked")
class AgentBehaviorTest {

    private static final String OLLAMA_BASE_URL = System.getProperty("ollama.base.url", "http://localhost:11434");
    private static final String MODEL_NAME = System.getProperty("ollama.model", "qwen3:8b");
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private static HttpClient httpClient;
    private static ObjectMapper objectMapper;
    private static Path behaviorLogDir;
    private static List<Map<String, Object>> behaviorLogs = new ArrayList<>();

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void setUpClass() throws IOException {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        objectMapper = new ObjectMapper();

        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        behaviorLogDir = tempDir.resolve("behavior-logs").resolve(dateStr);
        Files.createDirectories(behaviorLogDir);

        System.out.println("=== AgentBehaviorTest Setup ===");
        System.out.println("Ollama URL: " + OLLAMA_BASE_URL);
        System.out.println("Model: " + MODEL_NAME);
        System.out.println("================================");
    }

    @AfterAll
    static void tearDownClass() throws IOException {
        Path summaryPath = behaviorLogDir.resolve("_behavior-summary.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(summaryPath.toFile(), behaviorLogs);
        System.out.println("\n=== Behavior Test Summary ===");
        System.out.println("Total tests: " + behaviorLogs.size());
        long passed = behaviorLogs.stream().filter(l -> Boolean.TRUE.equals(l.get("passed"))).count();
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + (behaviorLogs.size() - passed));
        System.out.println("================================");
    }

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

    private void logBehavior(String testName, String category, boolean passed, String detail) {
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("testName", testName);
        log.put("category", category);
        log.put("passed", passed);
        log.put("detail", detail);
        log.put("model", MODEL_NAME);
        log.put("timestamp", LocalDateTime.now().toString());
        behaviorLogs.add(log);
    }

    @Nested
    @DisplayName("Memory Merge - 记忆合并行为")
    class MemoryMergeBehavior {

        @Test
        @DisplayName("behavior001: 新记忆应被确认记住")
        void shouldAcknowledgeNewMemory(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。当用户说\"记住\"某件事时，请明确确认你已记住。";
            String userPrompt = "记住：我最喜欢的编程语言是 Java";

            String output = callOllama(systemPrompt, userPrompt);

            boolean acknowledged = output.contains("记住") || output.contains("Java")
                    || output.contains("了解") || output.contains("知道") || output.contains("好的");

            logBehavior("behavior001", "memory-merge", acknowledged,
                    acknowledged ? "LLM确认记住了偏好" : "LLM未确认: " + truncate(output));
            assertThat(acknowledged)
                    .as("LLM should acknowledge remembering the preference")
                    .isTrue();
        }

        @Test
        @DisplayName("behavior002: 覆盖记忆时不应丢失旧信息")
        void shouldNotLoseOldInfoWhenOverwriting(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。用户之前喜欢 Java，现在要更新偏好。请确认更新并保留旧偏好的上下文。";
            String userPrompt = "我改变主意了，我现在更喜欢 Kotlin";

            String output = callOllama(systemPrompt, userPrompt);

            boolean hasContext = output.contains("Kotlin") && output.length() > 10;

            logBehavior("behavior002", "memory-merge", hasContext,
                    hasContext ? "LLM正确处理了偏好更新" : "LLM处理不完整: " + truncate(output));
            assertThat(output).isNotEmpty();
        }

        @Test
        @DisplayName("behavior003: 多条不相关记忆应独立存储")
        void shouldStoreUnrelatedMemoriesIndependently(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。用户会告诉你多条不相关的信息，请分别记住。";
            String userPrompt = "记住三件事：1) 我喜欢 Java 2) 我住在北京 3) 我的生日是1月1日";

            String output = callOllama(systemPrompt, userPrompt);

            boolean coversAll = (output.contains("Java") || output.contains("编程"))
                    && (output.contains("北京") || output.contains("住"))
                    && (output.contains("生日") || output.contains("1月"));

            logBehavior("behavior003", "memory-merge", coversAll,
                    coversAll ? "LLM记住了所有三条信息" : "LLM遗漏了部分信息: " + truncate(output));
            assertThat(output).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Reflection - 自我反思行为")
    class ReflectionBehavior {

        @Test
        @DisplayName("behavior004: 被纠正后应承认错误")
        void shouldAdmitMistakeWhenCorrected(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手，但偶尔会犯错。当用户纠正你时，请承认错误并给出正确答案。";
            String userPrompt = "你刚才说地球是平的，这是错误的。地球是球形的。请纠正。";

            String output = callOllama(systemPrompt, userPrompt);

            boolean admitted = output.contains("抱歉") || output.contains("纠正")
                    || output.contains("错误") || output.contains("正确")
                    || output.contains("对不") || output.contains("球形");

            logBehavior("behavior004", "reflection", admitted,
                    admitted ? "LLM承认了错误" : "LLM未承认错误: " + truncate(output));
            assertThat(admitted)
                    .as("LLM should admit mistake when corrected")
                    .isTrue();
        }

        @Test
        @DisplayName("behavior005: 不确定时应明确表达")
        void shouldExpressUncertainty(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。对于你无法确定的事情，请明确表达不确定性。";
            String userPrompt = "我的猫昨天吃了什么？";

            String output = callOllama(systemPrompt, userPrompt);

            boolean showsUncertainty = output.contains("不确定") || output.contains("无法")
                    || output.contains("不知道") || output.contains("无法得知")
                    || output.contains("没有") || output.contains("信息");

            logBehavior("behavior005", "reflection", showsUncertainty,
                    showsUncertainty ? "LLM表达了不确定性" : "LLM可能编造了答案: " + truncate(output));
            assertThat(output).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Planner - 规划行为")
    class PlannerBehavior {

        @Test
        @DisplayName("behavior006: 多步骤任务应分解")
        void shouldDecomposeMultiStepTask(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。对于复杂任务，请分解为清晰的步骤。";
            String userPrompt = "帮我创建一个 Spring Boot 项目：需要配置数据库、写一个 UserController、写单元测试。";

            String output = callOllama(systemPrompt, userPrompt);

            boolean hasSteps = output.contains("步骤") || output.contains("1")
                    || output.contains("首先") || output.contains("然后");

            logBehavior("behavior006", "planner", hasSteps,
                    hasSteps ? "LLM分解了任务" : "LLM未分解任务: " + truncate(output));
            assertThat(output).isNotEmpty();
        }

        @Test
        @DisplayName("behavior007: 简单问题不应过度规划")
        void shouldNotOverPlanSimpleQuestion(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。对于简单问题，直接回答，不要过度规划。";
            String userPrompt = "1+1等于几？";

            String output = callOllama(systemPrompt, userPrompt);

            boolean isSimple = output.contains("2") && output.length() < 200;

            logBehavior("behavior007", "planner", isSimple,
                    isSimple ? "LLM简洁回答了简单问题" : "LLM过度规划: " + truncate(output));
            assertThat(output).contains("2");
        }
    }

    @Nested
    @DisplayName("Skill - 技能执行行为")
    class SkillBehavior {

        @Test
        @DisplayName("behavior008: 代码生成应包含可运行代码")
        void shouldGenerateRunnableCode(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。生成代码时请确保代码可运行。";
            String userPrompt = "写一个 Java 方法，计算两个数的最大公约数";

            String output = callOllama(systemPrompt, userPrompt);

            boolean hasCode = output.contains("public") || output.contains("int")
                    || output.contains("return") || output.contains("```");

            logBehavior("behavior008", "skill", hasCode,
                    hasCode ? "LLM生成了代码" : "LLM未生成代码: " + truncate(output));
            assertThat(output).isNotEmpty();
        }

        @Test
        @DisplayName("behavior009: 文件路径应使用绝对路径")
        void shouldUseAbsolutePath(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。当用户提到文件路径时，总是使用完整绝对路径。";
            String userPrompt = "读取 C:\\\\Users\\\\test\\\\document.txt 文件";

            String output = callOllama(systemPrompt, userPrompt);

            boolean hasPath = output.contains("C:\\\\") || output.contains("C:/")
                    || output.contains("document.txt") || output.contains("路径");

            logBehavior("behavior009", "skill", hasPath,
                    hasPath ? "LLM使用了绝对路径" : "LLM未使用绝对路径: " + truncate(output));
            assertThat(output).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Reasoning - 推理行为")
    class ReasoningBehavior {

        @Test
        @DisplayName("behavior010: 因果推理应正确")
        void shouldReasonCorrectly(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。请进行正确的因果推理。";
            String userPrompt = "如果下雨地面会湿。现在地面湿了，能确定下过雨吗？";

            String output = callOllama(systemPrompt, userPrompt);

            boolean correct = output.contains("不一定") || output.contains("不能")
                    || output.contains("可能") || output.contains("其他");

            logBehavior("behavior010", "reasoning", correct,
                    correct ? "LLM推理正确" : "LLM推理错误: " + truncate(output));
            assertThat(correct)
                    .as("LLM should recognize that wet ground doesn't necessarily mean rain")
                    .isTrue();
        }
    }

    private String truncate(String s) {
        if (s == null) return "null";
        return s.length() <= 200 ? s : s.substring(0, 197) + "...";
    }
}