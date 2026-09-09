package com.mcp.engine.test.benchmark;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("llm")
@DisplayName("Real Agent Benchmark - 真实 LLM 基准测试")
@SuppressWarnings("unchecked")
class RealAgentBenchmark {

    private static final String OLLAMA_BASE_URL = System.getProperty("ollama.base.url", "http://localhost:11434");
    private static final String MODEL_NAME = System.getProperty("ollama.model", "qwen2:7b");
    private static final Duration TIMEOUT = Duration.ofSeconds(120);
    private static final String BENCHMARK_DIR = "benchmark-results";

    private static HttpClient httpClient;
    private static ObjectMapper objectMapper;
    private static Path resultDir;
    private static List<BenchmarkResult> allResults = new ArrayList<>();

    @BeforeAll
    static void setUpClass() throws IOException {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        objectMapper = new ObjectMapper();

        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        resultDir = Paths.get(BENCHMARK_DIR, dateStr);
        Files.createDirectories(resultDir);

        System.out.println("=== RealAgentBenchmark Setup ===");
        System.out.println("Ollama URL: " + OLLAMA_BASE_URL);
        System.out.println("Model: " + MODEL_NAME);
        System.out.println("Results dir: " + resultDir.toAbsolutePath());
        System.out.println("================================");
    }

    @AfterAll
    static void tearDownClass() throws IOException {
        Path summaryPath = resultDir.resolve("_summary.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(summaryPath.toFile(), allResults);
        System.out.println("\n=== Benchmark Summary ===");
        System.out.println("Total tasks: " + allResults.size());
        System.out.println("Success: " + allResults.stream().filter(BenchmarkResult::isSuccess).count());
        System.out.println("Failed: " + allResults.stream().filter(r -> !r.isSuccess()).count());
        System.out.println("Results saved to: " + resultDir.toAbsolutePath());
        System.out.println("========================");
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

    private BenchmarkResult recordResult(String taskId, String taskName, String category,
                                          String systemPrompt, String userPrompt,
                                          String llmOutput, long elapsedMs, boolean success,
                                          String error) {
        BenchmarkResult result = BenchmarkResult.builder()
                .taskId(taskId)
                .taskName(taskName)
                .category(category)
                .systemPrompt(truncate(systemPrompt, 200))
                .prompt(truncate(userPrompt, 200))
                .llmOutput(truncate(llmOutput, 1000))
                .elapsedTimeMs(elapsedMs)
                .success(success)
                .errorMessage(error)
                .modelName(MODEL_NAME)
                .executedAt(LocalDateTime.now())
                .version("0.0.1-SNAPSHOT")
                .build();

        allResults.add(result);

        try {
            Path resultPath = resultDir.resolve(taskId + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(resultPath.toFile(), result);
        } catch (IOException e) {
            System.err.println("Failed to save result: " + e.getMessage());
        }

        return result;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    @Nested
    @DisplayName("Memory - 记忆能力基准")
    class MemoryBenchmark {

        @Test
        @DisplayName("bench001: 记住用户偏好 — 游戏")
        void bench001_rememberPreference(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。用户会告诉你一些偏好，请记住它们。";
            String userPrompt = "以后记住：我最喜欢 Terraria";

            long start = System.currentTimeMillis();
            String output = callOllama(systemPrompt, userPrompt);
            long elapsed = System.currentTimeMillis() - start;

            boolean hasAcknowledged = output.contains("记住") || output.contains("Terraria")
                    || output.contains("偏好") || output.contains("喜欢")
                    || output.contains("了解") || output.contains("知道");

            recordResult("bench001", "记住偏好-游戏", "memory",
                    systemPrompt, userPrompt, output, elapsed, hasAcknowledged,
                    hasAcknowledged ? null : "LLM未确认记住偏好");
            assertThat(output).isNotEmpty();
        }

        @Test
        @DisplayName("bench002: 记住用户偏好 — 食物")
        void bench002_rememberFoodPreference(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。用户会告诉你一些偏好，请记住它们。";
            String userPrompt = "记住：我喜欢吃苹果";

            long start = System.currentTimeMillis();
            String output = callOllama(systemPrompt, userPrompt);
            long elapsed = System.currentTimeMillis() - start;

            boolean hasAcknowledged = output.contains("记住") || output.contains("苹果")
                    || output.contains("偏好") || output.contains("喜欢");

            recordResult("bench002", "记住偏好-食物", "memory",
                    systemPrompt, userPrompt, output, elapsed, hasAcknowledged,
                    hasAcknowledged ? null : "LLM未确认记住偏好");
            assertThat(output).isNotEmpty();
        }

        @Test
        @DisplayName("bench003: 覆盖已有偏好")
        void bench003_overwritePreference(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。用户之前说过喜欢 Terraria，现在他更新了偏好。";
            String userPrompt = "我改变主意了，我现在最喜欢 Minecraft 而不是 Terraria";

            long start = System.currentTimeMillis();
            String output = callOllama(systemPrompt, userPrompt);
            long elapsed = System.currentTimeMillis() - start;

            boolean hasUpdated = output.contains("Minecraft") || output.contains("更新")
                    || output.contains("改变") || output.contains("记住");

            recordResult("bench003", "覆盖偏好", "memory",
                    systemPrompt, userPrompt, output, elapsed, hasUpdated,
                    hasUpdated ? null : "LLM未正确更新偏好");
            assertThat(output).isNotEmpty();
        }

        @Test
        @DisplayName("bench004: 记忆合并 — 多条偏好不冲突")
        void bench004_mergePreferences(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。用户之前喜欢 Terraria，喜欢吃苹果。现在他告诉你新的偏好。";
            String userPrompt = "另外，我住在北京朝阳区，这个也记住";

            long start = System.currentTimeMillis();
            String output = callOllama(systemPrompt, userPrompt);
            long elapsed = System.currentTimeMillis() - start;

            boolean hasMerged = output.contains("北京") || output.contains("记住")
                    || output.contains("朝阳");

            recordResult("bench004", "记忆合并-多条偏好", "memory",
                    systemPrompt, userPrompt, output, elapsed, hasMerged,
                    hasMerged ? null : "LLM未正确合并新记忆");
            assertThat(output).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Reflection - 反思能力基准")
    class ReflectionBenchmark {

        @Test
        @DisplayName("bench005: 自我纠错 — 错误信息后修正")
        void bench005_selfCorrection(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。如果发现之前的回答有误，请主动纠正。";
            String userPrompt = "你刚才说 1+1=3，这是错的，请重新计算";

            long start = System.currentTimeMillis();
            String output = callOllama(systemPrompt, userPrompt);
            long elapsed = System.currentTimeMillis() - start;

            boolean hasCorrected = output.contains("2") || output.contains("纠正")
                    || output.contains("抱歉") || output.contains("正确");

            recordResult("bench005", "自我纠错", "reflection",
                    systemPrompt, userPrompt, output, elapsed, hasCorrected,
                    hasCorrected ? null : "LLM未进行自我纠正");
            assertThat(output).isNotEmpty();
        }

        @Test
        @DisplayName("bench006: 识别不确定性")
        void bench006_uncertaintyRecognition(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。对于不确定的事情，请明确表示不确定，不要编造。";
            String userPrompt = "2030年世界杯冠军是谁？";

            long start = System.currentTimeMillis();
            String output = callOllama(systemPrompt, userPrompt);
            long elapsed = System.currentTimeMillis() - start;

            boolean showsUncertainty = output.contains("不确定") || output.contains("无法")
                    || output.contains("尚未") || output.contains("还没有")
                    || output.contains("无法预测") || output.contains("不知道");

            recordResult("bench006", "识别不确定性", "reflection",
                    systemPrompt, userPrompt, output, elapsed, showsUncertainty,
                    showsUncertainty ? null : "LLM未识别不确定性，可能编造了答案");
            assertThat(output).isNotEmpty();
        }

        @Test
        @DisplayName("bench007: 重试 — 失败后重试")
        void bench007_retryAfterFailure(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。如果第一次操作失败，尝试换一种方式。";
            String userPrompt = "搜索\"Java 21 virtual threads\"但用中文回答";

            long start = System.currentTimeMillis();
            String output = callOllama(systemPrompt, userPrompt);
            long elapsed = System.currentTimeMillis() - start;

            boolean hasChinese = output.matches(".*[\\u4e00-\\u9fff].*");

            recordResult("bench007", "重试-失败后重试", "reflection",
                    systemPrompt, userPrompt, output, elapsed, hasChinese,
                    hasChinese ? null : "LLM未用中文回答");
            assertThat(output).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Planner - 规划能力基准")
    class PlannerBenchmark {

        @Test
        @DisplayName("bench008: 简单任务规划")
        void bench008_simpleTaskPlanning(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。对于复杂任务，先规划步骤再执行。";
            String userPrompt = "帮我分析一个 Java 项目的代码质量，步骤是：1) 统计代码行数 2) 检查命名规范 3) 给出改进建议。请规划你的执行方案。";

            long start = System.currentTimeMillis();
            String output = callOllama(systemPrompt, userPrompt);
            long elapsed = System.currentTimeMillis() - start;

            boolean hasPlan = output.contains("步骤") || output.contains("1")
                    || output.contains("方案") || output.contains("计划");

            recordResult("bench008", "简单任务规划", "planner",
                    systemPrompt, userPrompt, output, elapsed, hasPlan,
                    hasPlan ? null : "LLM未进行任务规划");
            assertThat(output).isNotEmpty();
        }

        @Test
        @DisplayName("bench009: 无需工具时的判断")
        void bench009_noToolNeeded(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手，你可以使用工具。但如果不必要，直接回答。";
            String userPrompt = "你好，今天天气怎么样？";

            long start = System.currentTimeMillis();
            String output = callOllama(systemPrompt, userPrompt);
            long elapsed = System.currentTimeMillis() - start;

            recordResult("bench009", "无需工具判断", "planner",
                    systemPrompt, userPrompt, output, elapsed, true, null);
            assertThat(output).isNotEmpty();
        }

        @Test
        @DisplayName("bench010: 选择合适工具")
        void bench010_chooseRightTool(TestInfo testInfo) throws Exception {
            String systemPrompt = """
                你是一个智能助手，可以使用以下工具：
                - search_file: 搜索文件
                - read_file: 读取文件内容
                - edit_file: 编辑文件
                请根据用户需求选择最合适的工具。""";
            String userPrompt = "我需要读取 C:\\\\project\\\\README.md 这个文件的内容";

            long start = System.currentTimeMillis();
            String output = callOllama(systemPrompt, userPrompt);
            long elapsed = System.currentTimeMillis() - start;

            boolean mentionsTool = output.contains("read_file") || output.contains("读取")
                    || output.contains("文件") || output.contains("README");

            recordResult("bench010", "选择合适的工具", "planner",
                    systemPrompt, userPrompt, output, elapsed, mentionsTool,
                    mentionsTool ? null : "LLM未识别需要文件读取工具");
            assertThat(output).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Prompt - Prompt 遵循能力基准")
    class PromptAdherenceBenchmark {

        @Test
        @DisplayName("bench011: 遵循输出格式要求")
        void bench011_followOutputFormat(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。请用 JSON 格式回答，包含 status 和 message 两个字段。";
            String userPrompt = "今天心情如何？";

            long start = System.currentTimeMillis();
            String output = callOllama(systemPrompt, userPrompt);
            long elapsed = System.currentTimeMillis() - start;

            boolean hasJson = output.contains("{") || output.contains("status")
                    || output.contains("message") || output.contains("json");

            recordResult("bench011", "遵循输出格式", "prompt",
                    systemPrompt, userPrompt, output, elapsed, hasJson,
                    hasJson ? null : "LLM未遵循JSON格式要求");
            assertThat(output).isNotEmpty();
        }

        @Test
        @DisplayName("bench012: 遵循角色扮演指令")
        void bench012_followRolePlay(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个专业的技术面试官，只问技术问题，不闲聊。";
            String userPrompt = "你好，给我讲个笑话";

            long start = System.currentTimeMillis();
            String output = callOllama(systemPrompt, userPrompt);
            long elapsed = System.currentTimeMillis() - start;

            boolean staysOnTopic = !output.contains("笑话") || output.contains("技术")
                    || output.contains("面试") || output.contains("问题");

            recordResult("bench012", "遵循角色扮演", "prompt",
                    systemPrompt, userPrompt, output, elapsed, staysOnTopic,
                    staysOnTopic ? null : "LLM未遵循角色扮演指令");
            assertThat(output).isNotEmpty();
        }

        @Test
        @DisplayName("bench013: 遵循字数限制")
        void bench013_followLengthLimit(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。请用不超过20个字回答所有问题。";
            String userPrompt = "介绍一下 Java 的历史";

            long start = System.currentTimeMillis();
            String output = callOllama(systemPrompt, userPrompt);
            long elapsed = System.currentTimeMillis() - start;

            boolean withinLimit = output.length() <= 150;

            recordResult("bench013", "遵循字数限制", "prompt",
                    systemPrompt, userPrompt, output, elapsed, withinLimit,
                    withinLimit ? null : "LLM输出超过字数限制 (" + output.length() + " chars)");
            assertThat(output).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Reasoning - 推理能力基准")
    class ReasoningBenchmark {

        @Test
        @DisplayName("bench014: 简单逻辑推理")
        void bench014_simpleLogic(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。请进行逻辑推理。";
            String userPrompt = "如果所有的猫都怕水，Tom 是一只猫，那么 Tom 怕水吗？";

            long start = System.currentTimeMillis();
            String output = callOllama(systemPrompt, userPrompt);
            long elapsed = System.currentTimeMillis() - start;

            boolean correct = output.contains("怕") || output.contains("是")
                    || output.contains("正确");

            recordResult("bench014", "简单逻辑推理", "reasoning",
                    systemPrompt, userPrompt, output, elapsed, correct,
                    correct ? null : "LLM逻辑推理错误");
            assertThat(output).isNotEmpty();
        }

        @Test
        @DisplayName("bench015: 多步推理")
        void bench015_multiStepReasoning(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。请进行多步推理。";
            String userPrompt = "小明有5个苹果，给了小红2个，又买了3个，现在有几个？";

            long start = System.currentTimeMillis();
            String output = callOllama(systemPrompt, userPrompt);
            long elapsed = System.currentTimeMillis() - start;

            boolean correct = output.contains("6");

            recordResult("bench015", "多步推理", "reasoning",
                    systemPrompt, userPrompt, output, elapsed, correct,
                    correct ? null : "LLM多步推理错误，期望6");
            assertThat(output).isNotEmpty();
        }

        @Test
        @DisplayName("bench016: 代码理解")
        void bench016_codeUnderstanding(TestInfo testInfo) throws Exception {
            String systemPrompt = "你是一个智能助手。请分析代码。";
            String userPrompt = "这段代码有什么问题？\n```java\npublic int add(int a, int b) {\n    return a - b;\n}\n```";

            long start = System.currentTimeMillis();
            String output = callOllama(systemPrompt, userPrompt);
            long elapsed = System.currentTimeMillis() - start;

            boolean detected = output.contains("减") || output.contains("subtract")
                    || output.contains("错误") || output.contains("bug")
                    || output.contains("问题") || output.contains("-")
                    || output.contains("命名");

            recordResult("bench016", "代码理解", "reasoning",
                    systemPrompt, userPrompt, output, elapsed, detected,
                    detected ? null : "LLM未发现代码中的减法错误");
            assertThat(output).isNotEmpty();
        }
    }
}