package com.mcp.engine.orchestrator;   // 请根据你的实际包路径调整

import com.mcp.core.domain.prompt.PromptType;
import com.mcp.core.service.ChatHistoryService;
import com.mcp.core.service.PromptService;
import com.mcp.engine.agent.Agent;
import com.mcp.engine.agent.AgentContext;
import com.mcp.engine.orchestrator.AgentOrchestrator;
import com.mcp.llm.client.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 默认 Agent 编排器 - 完整接入数据库 Prompt + 历史记录
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAgentOrchestrator implements AgentOrchestrator {

    private final LlmClient llmClient;
    private final PromptService promptService;
    private final ChatHistoryService chatHistoryService;

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    @Override
    public Mono<String> processRequest(String request, String sessionId) {
        if (request == null || request.trim().isEmpty()) {
            return Mono.just("请输入有效的问题。");
        }

        long startTime = System.currentTimeMillis();

        log.info("[Orchestrator] Receive request: {} | Session: {}", request, sessionId);

        return preloadFiles(request)
                .flatMap(fileContext -> Mono.zip(
                        promptService.getCoreSystemPrompt(),                    // 1. 获取系统 Prompt
                        chatHistoryService.getHistorySummary(sessionId, 10),    // 2. 获取历史摘要（最近10条）
                        Mono.just(fileContext)
                ))
                .flatMap(tuple -> {
                    String systemPrompt = tuple.getT1();
                    String historySummary = tuple.getT2();
                    String fileContext = tuple.getT3();
                    String enrichedSystemPrompt = (fileContext != null && !fileContext.isEmpty())
                            ? systemPrompt + "\n\n" + fileContext
                            : systemPrompt;

                    // 意图分类：判断是否需要工具调用
                    Agent defaultAgent = agents.isEmpty() ? null : agents.values().iterator().next();
                    if (defaultAgent != null && likelyNeedsTools(request)) {
                        log.info("[Orchestrator] Delegating to agent: {}", defaultAgent.getName());
                        return defaultAgent.executeWithContext(request,
                                        AgentContext.builder()
                                                .systemPrompt(enrichedSystemPrompt)
                                                .sessionId(sessionId)
                                                .build())
                                .flatMap(response ->
                                        chatHistoryService.saveUserAndAssistantMessage(sessionId, request, response)
                                                .thenReturn(response)
                                );
                    }

                    if (defaultAgent != null) {
                        log.info("[Orchestrator] Simple chat, using direct LLM (no tools)");
                    }

                    // 没有 Agent 时，直接调用 LLM
                    String userPrompt = buildUserPrompt(request, historySummary);
                    return llmClient.generateWithSystemPrompt(enrichedSystemPrompt, userPrompt);
                })
                .flatMap(response -> {
                    // 3. 保存对话历史
                    return chatHistoryService.saveUserAndAssistantMessage(sessionId, request, response)
                            .thenReturn(response);
                })
                .doOnSuccess(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("[Orchestrator] Success! Duration: {}ms | Session: {}", duration, sessionId);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("[Orchestrator] Error! Duration: {}ms | Error: {}", duration, error.getMessage(), error);
                })
                .onErrorResume(error -> {
                    String msg = error.getMessage() != null ? error.getMessage() : "Unknown error";
                    if (msg.contains("429")) {
                        return Mono.just("当前 Gemini API 配额已用尽，请稍后再试。");
                    }
                    return Mono.just("处理请求时发生错误: " + msg);
                });
    }

    /**
     * 构建带历史的用户 Prompt
     */
    private String buildUserPrompt(String request, String historySummary) {
        return """
            历史对话摘要：
            %s
            
            用户最新问题：%s
            
            请一步一步思考并给出专业、清晰的回答。
            """.formatted(historySummary.isEmpty() ? "（无历史对话）" : historySummary, request);
    }

    @Override
    public Mono<String> processRequestWithModel(String request, String sessionId, String modelConfigId) {
        if (request == null || request.trim().isEmpty()) {
            return Mono.just("请输入有效的问题。");
        }

        long startTime = System.currentTimeMillis();
        log.info("[Orchestrator] Receive request: {} | Session: {} | Model: {}", request, sessionId, modelConfigId);

        return preloadFiles(request)
                .flatMap(fileContext -> Mono.zip(
                        promptService.getCoreSystemPrompt(),
                        chatHistoryService.getHistorySummary(sessionId, 10),
                        Mono.just(fileContext)
                ))
                .flatMap(tuple -> {
                    String systemPrompt = tuple.getT1();
                    String historySummary = tuple.getT2();
                    String fileContext = tuple.getT3();
                    String enrichedSystemPrompt = (fileContext != null && !fileContext.isEmpty())
                            ? systemPrompt + "\n\n" + fileContext
                            : systemPrompt;
                    String userPrompt = buildUserPrompt(request, historySummary);

                    Agent defaultAgent = agents.isEmpty() ? null : agents.values().iterator().next();
                    if (defaultAgent != null && likelyNeedsTools(request)) {
                        log.info("[Orchestrator] Delegating to agent: {}", defaultAgent.getName());
                        return defaultAgent.executeWithContext(request,
                                        AgentContext.builder()
                                                .systemPrompt(enrichedSystemPrompt)
                                                .sessionId(sessionId)
                                                .build())
                                .flatMap(response ->
                                        chatHistoryService.saveUserAndAssistantMessage(sessionId, request, response)
                                                .thenReturn(response)
                                )
                                .doOnSuccess(result -> {
                                    long duration = System.currentTimeMillis() - startTime;
                                    log.info("[Orchestrator] Agent Success! Duration: {}ms | Session: {} | Model: {}", duration, sessionId, modelConfigId);
                                })
                                .doOnError(error -> {
                                    long duration = System.currentTimeMillis() - startTime;
                                    log.error("[Orchestrator] Agent Error! Duration: {}ms | Error: {}", duration, error.getMessage(), error);
                                })
                                .onErrorResume(error ->
                                        Mono.just("处理请求时发生错误: " + error.getMessage())
                                );
                    }

                    if (modelConfigId != null && !modelConfigId.isEmpty()) {
                        return llmClient.generateWithConfigAndSystem(modelConfigId, enrichedSystemPrompt, userPrompt);
                    }
                    return llmClient.generateWithSystemPrompt(enrichedSystemPrompt, userPrompt);
                })
                .flatMap(response ->
                        chatHistoryService.saveUserAndAssistantMessage(sessionId, request, response)
                                .thenReturn(response)
                )
                .doOnSuccess(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("[Orchestrator] Success! Duration: {}ms | Session: {} | Model: {}", duration, sessionId, modelConfigId);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("[Orchestrator] Error! Duration: {}ms | Error: {}", duration, error.getMessage(), error);
                })
                .onErrorResume(error ->
                        Mono.just("处理请求时发生错误: " + error.getMessage())
                );
    }

    @Override
    public Mono<String> processRequestWithSystemPrompt(String request, String sessionId, String systemPrompt, String modelConfigId) {
        if (request == null || request.trim().isEmpty()) {
            return Mono.just("请输入有效的问题。");
        }

        long startTime = System.currentTimeMillis();
        final String effectiveSystemPrompt = (systemPrompt != null && !systemPrompt.isBlank())
                ? systemPrompt
                : null;
        log.info("[Orchestrator] Receive request: {} | Session: {} | SystemPrompt: {} | Model: {}",
                request, sessionId,
                effectiveSystemPrompt != null
                        ? effectiveSystemPrompt.substring(0, Math.min(30, effectiveSystemPrompt.length())) + "..."
                        : "(using core prompt)",
                modelConfigId != null ? modelConfigId : "default");

        Mono<String> systemPromptMono = (effectiveSystemPrompt != null)
                ? Mono.just(effectiveSystemPrompt)
                : promptService.getCoreSystemPrompt();

        return systemPromptMono
                .flatMap(resolvedPrompt ->
                    preloadFiles(request)
                    .flatMap(fileContext -> {
                        final String enrichedSystemPrompt;
                        if (fileContext != null && !fileContext.isEmpty()) {
                            enrichedSystemPrompt = resolvedPrompt + "\n\n" + fileContext;
                            log.info("[Orchestrator] Preloaded {} file(s) into context", fileContext.lines().filter(l -> l.startsWith("---")).count());
                        } else {
                            enrichedSystemPrompt = resolvedPrompt;
                        }

                    Agent defaultAgent = agents.isEmpty() ? null : agents.values().iterator().next();
                    if (defaultAgent != null && likelyNeedsTools(request)) {
                        log.info("[Orchestrator] Delegating to agent: {}", defaultAgent.getName());
                        return defaultAgent.executeWithContext(request,
                                        AgentContext.builder()
                                                .systemPrompt(enrichedSystemPrompt)
                                                .sessionId(sessionId)
                                                .build())
                                .flatMap(response ->
                                        chatHistoryService.saveUserAndAssistantMessage(sessionId, request, response)
                                                .thenReturn(response)
                                )
                                .doOnSuccess(result -> {
                                    long duration = System.currentTimeMillis() - startTime;
                                    log.info("[Orchestrator] Agent Success! Duration: {}ms | Session: {}", duration, sessionId);
                                })
                                .doOnError(error -> {
                                    long duration = System.currentTimeMillis() - startTime;
                                    log.error("[Orchestrator] Agent Error! Duration: {}ms | Error: {}", duration, error.getMessage(), error);
                                })
                                .onErrorResume(error ->
                                        Mono.just("处理请求时发生错误: " + error.getMessage())
                                );
                    }

                    return chatHistoryService.getHistorySummary(sessionId, 10)
                            .flatMap(historySummary -> {
                                String userPrompt = historySummary.isEmpty()
                                        ? request
                                        : "历史对话摘要：%s\n\n当前问题：%s".formatted(historySummary, request);
                                if (modelConfigId != null && !modelConfigId.isEmpty()) {
                                    return llmClient.generateWithConfigAndSystem(modelConfigId, enrichedSystemPrompt, userPrompt);
                                }
                                return llmClient.generateWithSystemPrompt(enrichedSystemPrompt, userPrompt);
                            })
                            .flatMap(response ->
                                    chatHistoryService.saveUserAndAssistantMessage(sessionId, request, response)
                                            .thenReturn(response)
                            )
                            .doOnSuccess(result -> {
                                long duration = System.currentTimeMillis() - startTime;
                                log.info("[Orchestrator] Success! Duration: {}ms | Session: {}", duration, sessionId);
                            })
                            .doOnError(error -> {
                                long duration = System.currentTimeMillis() - startTime;
                                log.error("[Orchestrator] Error! Duration: {}ms | Error: {}", duration, error.getMessage(), error);
                            })
                            .onErrorResume(error ->
                                    Mono.just("处理请求时发生错误: " + error.getMessage())
                            );
                })
        );
    }

    private static final Pattern WINDOWS_PATH_PATTERN =
            Pattern.compile("[A-Za-z]:\\\\\\S+", Pattern.CASE_INSENSITIVE);

    private static final Pattern FILENAME_PATTERN =
            Pattern.compile("[^\\s.,;:!?，。；：！？\"'<>`|]+\\.\\w{1,10}", Pattern.CASE_INSENSITIVE);

    private Mono<String> preloadFiles(String request) {
        return Mono.fromCallable(() -> {
            Set<String> paths = new LinkedHashSet<>();
            Matcher matcher = WINDOWS_PATH_PATTERN.matcher(request);
            while (matcher.find()) {
                String rawPath = matcher.group();
                String cleanPath = rawPath.replaceAll("[，。；！？、\"'<>`]$", "").trim();
                paths.add(cleanPath);
            }

            if (paths.isEmpty()) {
                return "";
            }

            List<String> filenames = new ArrayList<>();
            Matcher fnMatcher = FILENAME_PATTERN.matcher(request);
            while (fnMatcher.find()) {
                filenames.add(fnMatcher.group());
            }

            StringBuilder sb = new StringBuilder();
            sb.append("【已预加载的文件内容】\n");
            sb.append("以下文件内容由系统自动读取并注入上下文，你可以直接基于这些内容进行分析：\n\n");

            for (String filePath : paths) {
                try {
                    Path p = Path.of(filePath).toAbsolutePath().normalize();
                    if (Files.isDirectory(p)) {
                        sb.append("--- 目录: ").append(filePath).append(" ---\n");
                        List<Path> dirFiles = new ArrayList<>();
                        try (var stream = Files.list(p)) {
                            List<String> entries = new ArrayList<>();
                            stream.forEach(f -> {
                                entries.add((Files.isDirectory(f) ? "[DIR]  " : "[FILE] ") + f.getFileName());
                                if (Files.isRegularFile(f) && Files.isReadable(f)) {
                                    dirFiles.add(f);
                                }
                            });
                            for (String entry : entries) {
                                sb.append(entry).append("\n");
                            }
                        }
                        sb.append("\n");
                        log.info("[Orchestrator] Listed directory: {}", filePath);

                        for (String fn : filenames) {
                            Path resolved = p.resolve(fn).normalize();
                            if (Files.isRegularFile(resolved) && Files.isReadable(resolved)
                                    && !resolved.getParent().equals(p)) {
                                continue;
                            }
                            if (Files.isRegularFile(resolved) && Files.isReadable(resolved)) {
                                String content = Files.readString(resolved);
                                sb.append("--- 文件: ").append(resolved).append(" ---\n");
                                sb.append(content).append("\n\n");
                                log.info("[Orchestrator] Preloaded file from dir: {} ({} chars)", resolved, content.length());
                            }
                        }
                    } else if (Files.isRegularFile(p) && Files.isReadable(p)) {
                        String content = Files.readString(p);
                        sb.append("--- 文件: ").append(filePath).append(" ---\n");
                        sb.append(content).append("\n\n");
                        log.info("[Orchestrator] Preloaded file: {} ({} chars)", filePath, content.length());
                    } else {
                        sb.append("--- 文件: ").append(filePath).append(" (路径不存在或不可读) ---\n\n");
                        log.warn("[Orchestrator] Cannot read path: {}", filePath);
                    }
                } catch (IOException e) {
                    sb.append("--- 文件: ").append(filePath).append(" (读取失败: ").append(e.getMessage()).append(") ---\n\n");
                    log.error("[Orchestrator] Failed to read file: {}", filePath, e);
                }
            }

            return sb.toString();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<String> executeTask(String task, String agentName) {
        Agent agent = agents.get(agentName);
        if (agent == null) {
            return Mono.error(new RuntimeException("Agent not found: " + agentName));
        }
        return agent.execute(task);
    }

    @Override
    public void registerAgent(Agent agent) {
        agents.put(agent.getName(), agent);
        log.info("[Orchestrator] Agent registered: {}", agent.getName());
    }

    /**
     * 简单意图分类：判断用户请求是否可能需要调用工具
     */
    private boolean likelyNeedsTools(String request) {
        if (request == null || request.trim().isEmpty()) {
            return false;
        }
        String lower = request.toLowerCase();
        // 文件操作相关
        boolean fileRelated = lower.contains("文件") || lower.contains("路径") || lower.contains("目录")
                || lower.contains("file") || lower.contains("path") || lower.contains("folder")
                || lower.contains("读取") || lower.contains("写入") || lower.contains("编辑")
                || lower.contains("read") || lower.contains("write") || lower.contains("edit");
        // 搜索相关
        boolean searchRelated = lower.contains("搜索") || lower.contains("查找") || lower.contains("查询")
                || lower.contains("search") || lower.contains("find") || lower.contains("lookup");
        return fileRelated || searchRelated;
    }

    @Override
    public void registerDefaultTools() {
        // TODO: 后续实现工具自动注册
        log.info("[Orchestrator] Default tools registration placeholder.");
    }
}