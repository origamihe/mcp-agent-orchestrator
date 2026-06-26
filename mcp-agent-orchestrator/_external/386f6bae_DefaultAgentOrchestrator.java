package com.mcp.engine.orchestrator;  

import com.mcp.core.domain.prompt.PromptType;
import com.mcp.core.domain.memory.SkillEntity;
import com.mcp.core.service.ChatHistoryService;
import com.mcp.core.service.LongTermMemoryService;
import com.mcp.core.service.PromptService;
import com.mcp.engine.agent.Agent;
import com.mcp.engine.agent.AgentContext;
import com.mcp.engine.agent.ExecutionTracker;
import com.mcp.engine.context.ContextBundle;
import com.mcp.engine.context.ContextManager;
import com.mcp.engine.context.ContextRequest;
import com.mcp.engine.memory.MemoryLifecycleOrchestrator;
import com.mcp.engine.orchestrator.AgentOrchestrator;
import com.mcp.engine.planner.EditPlan;
import com.mcp.engine.planner.PlanContext;
import com.mcp.engine.planner.Planner;
import com.mcp.engine.reflection.FailureLibraryService;
import com.mcp.engine.reflection.LearningBudgetManager;
import com.mcp.engine.reflection.PromptEnricher;
import com.mcp.engine.reflection.ReflectionAgent;
import com.mcp.engine.reflection.SkillGraphService;
import com.mcp.engine.reflection.SkillLibraryService;
import com.mcp.engine.reflection.TaskEvaluator;
import com.mcp.common.channel.IntentType;
import com.mcp.common.channel.RecallMode;
import com.mcp.llm.client.LlmClient;
import com.mcp.tools.registry.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Set;

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
    private final LongTermMemoryService memoryService;
    private final MemoryLifecycleOrchestrator memoryLifecycleOrchestrator;
    private final Planner planner;
    private final ContextManager contextManager;
    private final ToolRegistry toolRegistry;
    private final PromptEnricher promptEnricher;
    private final TaskEvaluator taskEvaluator;
    private final ReflectionAgent reflectionAgent;
    private final LearningBudgetManager learningBudgetManager;
    private final SkillLibraryService skillLibraryService;
    private final FailureLibraryService failureLibraryService;
    private final SkillGraphService skillGraphService;

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    @Value("${recall.max-history-tokens:5000}")
    private int maxHistoryTokens;

    @Override
    public Mono<String> processRequest(String request, String sessionId) {
        if (request == null || request.trim().isEmpty()) {
            return Mono.just("请输入有效的问题。");
        }

        long startTime = System.currentTimeMillis();
        log.info("[Orchestrator] Receive request: {} | Session: {}", request, sessionId);

        PlanContext planCtx = PlanContext.builder()
                .availableTools(toolRegistry.getAllTools())
                .sessionId(sessionId)
                .maxSteps(8)
                .build();

        return planner.plan(request, planCtx)
                .flatMap(plan -> {
                    log.info("[Orchestrator] Plan: type={}, steps={}, complexity={}",
                            plan.getPlanType(), plan.getSteps().size(), plan.getEstimatedComplexity());

                    List<String> filePaths = extractFilePathsFromPlan(plan, request);
                    ContextRequest ctxReq = ContextRequest.builder()
                            .sessionId(sessionId)
                            .filePaths(filePaths)
                            .userRequest(request)
                            .build();

                    return contextManager.buildContext(plan, ctxReq)
                            .flatMap(contextBundle -> preloadFiles(request)
                                    .flatMap(fileContext -> Mono.zip(
                                            promptService.getCoreSystemPrompt(),
                                            memoryService.buildWorkingContext(sessionId),
                                            Mono.just(fileContext),
                                            Mono.just(plan),
                                            Mono.just(contextBundle)
                                    )));
                })
                .flatMap(tuple -> {
                    String systemPrompt = tuple.getT1();
                    String memoryContext = tuple.getT2();
                    String fileContext = tuple.getT3();
                    EditPlan plan = tuple.getT4();
                    ContextBundle contextBundle = tuple.getT5();
                    String enrichedSystemPrompt = buildEnrichedPrompt(systemPrompt, fileContext, memoryContext);

                    return promptEnricher.enrich(request)
                            .map(enrichment -> {
                                String fullPrompt = enrichedSystemPrompt;
                                if (!enrichment.isEmpty()) {
                                    fullPrompt = enrichedSystemPrompt + "\n\n" + enrichment.promptText();
                                    log.info("[Orchestrator] Prompt enriched: {} skills, {} failures",
                                            enrichment.matchedSkills().size(),
                                            enrichment.matchedFailures().size());
                                }
                                return new Object[]{fullPrompt, plan, contextBundle, memoryContext, enrichment};
                            });
                })
                .flatMap(tuple -> {
                    String fullPrompt = (String) tuple[0];
                    EditPlan plan = (EditPlan) tuple[1];
                    ContextBundle contextBundle = (ContextBundle) tuple[2];
                    String memoryContext = (String) tuple[3];
                    PromptEnricher.EnrichmentResult enrichment = (PromptEnricher.EnrichmentResult) tuple[4];

                    ExecutionTracker tracker = new ExecutionTracker();
                    enrichment.matchedSkills().forEach(s -> tracker.addMatchedSkill(s.getId()));
                    enrichment.matchedFailures().forEach(f -> tracker.addMatchedFailure(f.getId()));

                    Agent defaultAgent = agents.isEmpty() ? null : agents.values().iterator().next();
                    if (defaultAgent != null && plan.needsTools()) {
                        log.info("[Orchestrator] Delegating to agent with plan: {}", defaultAgent.getName());
                        return defaultAgent.executeWithContext(request,
                                        AgentContext.builder()
                                                .systemPrompt(fullPrompt)
                                                .sessionId(sessionId)
                                                .editPlan(plan)
                                                .contextBundle(contextBundle)
                                                .memory(memoryContext)
                                                .executionTracker(tracker)
                                                .build())
                                .flatMap(response ->
                                        chatHistoryService.saveUserAndAssistantMessage(sessionId, request, response)
                                                .then(memoryService.checkAndCompressIfNeeded(sessionId))
                                                .thenReturn(response)
                                )
                                .doOnSuccess(response -> {
                                    recordSkillExecutions(enrichment, tracker);
                                    triggerReflection(sessionId, null, request,
                                            tracker.buildExecutionSummary(),
                                            tracker.buildToolsUsedSummary(), response,
                                            tracker.buildErrorSummary());
                                });
                    }

                    if (defaultAgent != null) {
                        log.info("[Orchestrator] Simple chat, using direct LLM (no tools)");
                    }

                    String userPrompt = "用户消息：" + request;
                    return llmClient.generateWithSystemPrompt(fullPrompt, userPrompt);
                })
                .flatMap(response ->
                        chatHistoryService.saveUserAndAssistantMessage(sessionId, request, response)
                                .then(memoryService.checkAndCompressIfNeeded(sessionId))
                                .then(memoryLifecycleOrchestrator.processMemoryLifecycle(
                                        sessionId, null, null,
                                        "用户: " + request + "\n助手: " + response))
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
                        memoryService.buildWorkingContext(sessionId),
                        Mono.just(fileContext),
                        promptEnricher.enrich(request)
                ))
                .flatMap(tuple -> {
                    String systemPrompt = tuple.getT1();
                    String memoryContext = tuple.getT2();
                    String fileContext = tuple.getT3();
                    PromptEnricher.EnrichmentResult enrichment = tuple.getT4();
                    String enrichedSystemPrompt = buildEnrichedPrompt(systemPrompt, fileContext, memoryContext);

                    String fullPrompt = enrichedSystemPrompt;
                    if (!enrichment.isEmpty()) {
                        fullPrompt = enrichedSystemPrompt + "\n\n" + enrichment.promptText();
                        log.info("[Orchestrator] Prompt enriched: {} skills, {} failures",
                                enrichment.matchedSkills().size(),
                                enrichment.matchedFailures().size());
                    }

                    String userPrompt = "用户消息：" + request;

                    Agent defaultAgent = agents.isEmpty() ? null : agents.values().iterator().next();
                    if (defaultAgent != null && likelyNeedsTools(request)) {
                        ExecutionTracker tracker = new ExecutionTracker();
                        enrichment.matchedSkills().forEach(s -> tracker.addMatchedSkill(s.getId()));
                        enrichment.matchedFailures().forEach(f -> tracker.addMatchedFailure(f.getId()));

                        log.info("[Orchestrator] Delegating to agent: {}", defaultAgent.getName());
                        return defaultAgent.executeWithContext(request,
                                        AgentContext.builder()
                                                .systemPrompt(fullPrompt)
                                                .sessionId(sessionId)
                                                .executionTracker(tracker)
                                                .build())
                                .flatMap(response ->
                                        chatHistoryService.saveUserAndAssistantMessage(sessionId, request, response)
                                                .then(memoryService.checkAndCompressIfNeeded(sessionId))
                                                .thenReturn(response)
                                )
                                .doOnSuccess(response -> {
                                    recordSkillExecutions(enrichment, tracker);
                                    triggerReflection(sessionId, null, request,
                                            tracker.buildExecutionSummary(),
                                            tracker.buildToolsUsedSummary(), response,
                                            tracker.buildErrorSummary());
                                });
                    }

                    if (modelConfigId != null && !modelConfigId.isEmpty()) {
                        return llmClient.generateWithConfigAndSystem(modelConfigId, fullPrompt, userPrompt);
                    }
                    return llmClient.generateWithSystemPrompt(fullPrompt, userPrompt);
                })
                .flatMap(response ->
                        chatHistoryService.saveUserAndAssistantMessage(sessionId, request, response)
                                .then(memoryService.checkAndCompressIfNeeded(sessionId))
                                .then(memoryLifecycleOrchestrator.processMemoryLifecycle(
                                        sessionId, null, null,
                                        "用户: " + request + "\n助手: " + response))
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
                        Mono.zip(
                                memoryService.buildWorkingContext(sessionId),
                                preloadFiles(request),
                                promptEnricher.enrich(request)
                        )
                        .flatMap(tuple -> {
                            String memoryContext = tuple.getT1();
                            String fileContext = tuple.getT2();
                            PromptEnricher.EnrichmentResult enrichment = tuple.getT3();
                            String enrichedPrompt = buildEnrichedPrompt(resolvedPrompt, fileContext, memoryContext);

                            String fullPrompt = enrichedPrompt;
                            if (!enrichment.isEmpty()) {
                                fullPrompt = enrichedPrompt + "\n\n" + enrichment.promptText();
                                log.info("[Orchestrator] Prompt enriched: {} skills, {} failures",
                                        enrichment.matchedSkills().size(),
                                        enrichment.matchedFailures().size());
                            }

                            Agent defaultAgent = agents.isEmpty() ? null : agents.values().iterator().next();
                            if (defaultAgent != null && likelyNeedsTools(request)) {
                                ExecutionTracker tracker = new ExecutionTracker();
                                enrichment.matchedSkills().forEach(s -> tracker.addMatchedSkill(s.getId()));
                                enrichment.matchedFailures().forEach(f -> tracker.addMatchedFailure(f.getId()));

                                log.info("[Orchestrator] Delegating to agent: {}", defaultAgent.getName());
                                return defaultAgent.executeWithContext(request,
                                                AgentContext.builder()
                                                        .systemPrompt(fullPrompt)
                                                        .sessionId(sessionId)
                                                        .executionTracker(tracker)
                                                        .build())
                                        .flatMap(response ->
                                                chatHistoryService.saveUserAndAssistantMessage(sessionId, request, response)
                                                        .then(memoryService.checkAndCompressIfNeeded(sessionId))
                                                        .thenReturn(response)
                                        )
                                        .doOnSuccess(response -> {
                                            recordSkillExecutions(enrichment, tracker);
                                            triggerReflection(sessionId, null, request,
                                                    tracker.buildExecutionSummary(),
                                                    tracker.buildToolsUsedSummary(), response,
                                                    tracker.buildErrorSummary());
                                        });
                            }

                            String userPrompt = "用户消息：" + request;
                            if (modelConfigId != null && !modelConfigId.isEmpty()) {
                                return llmClient.generateWithConfigAndSystem(modelConfigId, fullPrompt, userPrompt);
                            }
                            return llmClient.generateWithSystemPrompt(fullPrompt, userPrompt);
                        })
                        .flatMap(response ->
                                chatHistoryService.saveUserAndAssistantMessage(sessionId, request, response)
                                        .then(memoryService.checkAndCompressIfNeeded(sessionId))
                                        .thenReturn(response))
                )
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
                        return Mono.just("当前 API 配额已用尽，请稍后再试。");
                    }
                    return Mono.just("处理请求时发生错误: " + msg);
                });
    }

    private static final Pattern WINDOWS_PATH_PATTERN =
            Pattern.compile("[A-Za-z]:\\\\\\S+", Pattern.CASE_INSENSITIVE);

    private static final Pattern FILENAME_PATTERN =
            Pattern.compile("[^\\s.,;:!?，。；：！？\"'<>`|]+\\.\\w{1,10}", Pattern.CASE_INSENSITIVE);

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".txt", ".md", ".java", ".py", ".js", ".ts", ".json", ".xml",
            ".yaml", ".yml", ".properties", ".gradle", ".html", ".css", ".sql",
            ".sh", ".bat", ".cfg", ".conf", ".ini", ".log", ".csv", ".kt",
            ".go", ".rs", ".c", ".cpp", ".h", ".hpp", ".cs", ".php", ".rb", ".scala"
    );

    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(".docx", ".pdf");

    private static boolean isTextFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return TEXT_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static boolean isDocumentFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return DOCUMENT_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

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
    public Mono<String> processRequestWithHistory(String request, String sessionId, String systemPrompt,
                                                   RecallMode recallMode) {
        if (request == null || request.trim().isEmpty()) {
            return Mono.just("请输入有效的问题。");
        }

        long startTime = System.currentTimeMillis();
        boolean userOnly = (recallMode == RecallMode.USER_ONLY);
        log.info("[Orchestrator] RECALL_HISTORY ({} mode) request: {} | Session: {}",
                recallMode, request, sessionId);

        Mono<String> systemPromptMono = (systemPrompt != null && !systemPrompt.isBlank())
                ? Mono.just(systemPrompt)
                : promptService.getCoreSystemPrompt();

        return systemPromptMono
                .flatMap(resolvedPrompt ->
                        chatHistoryService.getSessionMessages(sessionId)
                                .flatMap(historyMessages -> {
                                    java.util.List<com.mcp.core.domain.chat.CoreChatMessage> messages =
                                            historyMessages.stream()
                                                    .map(e -> {
                                                        com.mcp.core.domain.chat.CoreChatMessage dm =
                                                                new com.mcp.core.domain.chat.CoreChatMessage();
                                                        dm.setMessageId(String.valueOf(e.getId()));
                                                        dm.setSessionId(e.getSessionId());
                                                        dm.setSenderId(e.getSenderId());
                                                        dm.setSenderName(e.getSenderName());
                                                        dm.setRole(e.getRole());
                                                        dm.setContent(e.getContent());
                                                        dm.setCreatedAt(e.getCreatedAt());
                                                        return dm;
                                                    })
                                                    .collect(Collectors.toList());

                                    String historyContext = buildHistoryContext(messages, userOnly);

                                    log.info("[Orchestrator] History loaded for session {}: total={}, userOnly={}, " +
                                                    "firstTime={}, lastTime={}, hasUserMsg={}, hasAssistantMsg={}",
                                            sessionId,
                                            messages.size(),
                                            userOnly,
                                            messages.isEmpty() ? "N/A" : messages.get(0).getCreatedAt(),
                                            messages.isEmpty() ? "N/A" : messages.get(messages.size() - 1).getCreatedAt(),
                                            messages.stream().anyMatch(m -> m.getRole() == com.mcp.core.domain.chat.MessageRole.USER),
                                            messages.stream().anyMatch(m -> m.getRole() == com.mcp.core.domain.chat.MessageRole.ASSISTANT));

                                    String userPrompt = buildRecallHistoryPrompt(request, historyContext, recallMode);

                                    return llmClient.generateWithSystemPrompt(resolvedPrompt, userPrompt);
                                })
                )
                .flatMap(response ->
                    chatHistoryService.touchSession(sessionId)
                            .then(memoryService.checkAndCompressIfNeeded(sessionId))
                            .thenReturn(response)
                )
                .doOnSuccess(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("[Orchestrator] RECALL_HISTORY ({} mode) success! Duration: {}ms | Session: {}", recallMode, duration, sessionId);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("[Orchestrator] RECALL_HISTORY error! Duration: {}ms | Error: {}", duration, error.getMessage(), error);
                })
                .onErrorResume(error ->
                        Mono.just("回顾聊天记录时发生错误: " + error.getMessage())
                );
    }

    /**
     * 将 CoreChatMessage 列表格式化为可读的历史上下文
     * 按总 token 预算截断，优先保留最近消息，超出部分从最早消息开始丢弃
     */
    private String buildHistoryContext(java.util.List<com.mcp.core.domain.chat.CoreChatMessage> messages,
                                        boolean userOnly) {
        if (messages == null || messages.isEmpty()) {
            return "（暂无历史对话记录）";
        }
        java.util.List<com.mcp.core.domain.chat.CoreChatMessage> filtered = userOnly
                ? messages.stream()
                    .filter(m -> m.getRole() == com.mcp.core.domain.chat.MessageRole.USER)
                    .collect(Collectors.toList())
                : messages;

        if (filtered.isEmpty()) {
            return "（暂无" + (userOnly ? "用户" : "") + "历史消息记录）";
        }

        String label = userOnly ? "用户消息记录" : "真实聊天记录";
        int totalTokens = 0;
        boolean truncated = false;
        java.util.List<String> entries = new java.util.ArrayList<>();

        for (int i = filtered.size() - 1; i >= 0; i--) {
            com.mcp.core.domain.chat.CoreChatMessage msg = filtered.get(i);
            String senderLabel = resolveSenderLabel(msg);
            String content = msg.getContent();
            if (content == null) content = "";
            if (content.length() > 2000) {
                content = content.substring(0, 2000) + "...";
            }
            String entry = "[" + senderLabel + "] " + content + "\n";
            int entryTokens = estimateTokens(entry);
            if (totalTokens + entryTokens > maxHistoryTokens) {
                truncated = true;
                break;
            }
            entries.add(entry);
            totalTokens += entryTokens;
        }

        java.util.Collections.reverse(entries);
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(label).append("（共 ").append(filtered.size()).append(" 条消息");
        if (truncated) {
            sb.append("，以下展示最近 ").append(entries.size()).append(" 条");
        }
        sb.append("） ===\n");
        if (truncated) {
            sb.append("...（更早的消息因上下文预算已省略）\n");
        }
        int seq = 1;
        for (String entry : entries) {
            sb.append("[").append(seq++).append("] ").append(entry);
        }
        sb.append("=== ").append(label).append("结束 ===");
        return sb.toString();
    }

    /**
     * 构建 RECALL_HISTORY 专用的用户 Prompt
     */
    private String buildRecallHistoryPrompt(String userRequest, String historyContext, RecallMode recallMode) {
        String taskDesc = switch (recallMode) {
            case USER_ONLY -> "用户要求回顾自己说过的话，以下仅列出用户发送的消息：";
            case CONVERSATION -> "用户要求回顾完整聊天对话，以下是该会话的真实聊天历史：";
            case BOTH -> "用户要求同时回顾自己说过的话和完整聊天记录，请先列出用户消息，再列出完整对话：";
        };
        String instructions = switch (recallMode) {
            case USER_ONLY -> """
                    要求：
                    1. 只列出用户发送过的消息，按编号逐条回复
                    2. 必须基于真实聊天记录，不要编造内容
                    3. 如果用户要求"逐条列出"或"全部列举"，请完整列出所有用户消息
                    4. 如果聊天记录为空，请如实告知用户
                    """;
            case CONVERSATION -> """
                    要求：
                    1. 必须基于真实聊天记录回答，不要编造内容
                    2. 如果用户要求"逐条列出"，请按编号逐条回复（包含用户和助手双方）
                    3. 如果用户要求"总结"，请按主题或时间线归纳
                    4. 如果聊天记录为空，请如实告知用户
                    """;
            case BOTH -> """
                    要求：
                    1. 先列出用户消息部分（仅用户发送的消息）
                    2. 再列出完整对话部分（包含用户和助手双方）
                    3. 必须基于真实聊天记录，不要编造内容
                    4. 如果聊天记录为空，请如实告知用户
                    """;
        };
        return """
                %s
                
                %s
                
                用户的具体请求：%s
                
                %s
                """.formatted(taskDesc, historyContext, userRequest, instructions);
    }

    private List<String> extractFilePathsFromPlan(EditPlan plan, String userRequest) {
        List<String> paths = new ArrayList<>();
        if (plan.getSteps() == null) {
            return paths;
        }
        for (var step : plan.getSteps()) {
            if (step.getArguments() != null) {
                Object path = step.getArguments().getOrDefault("path",
                        step.getArguments().get("filePath"));
                if (path instanceof String s && !s.isBlank()) {
                    paths.add(s);
                }
            }
        }
        return paths;
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

    private String resolveSenderLabel(com.mcp.core.domain.chat.CoreChatMessage msg) {
        if (msg.getRole() == com.mcp.core.domain.chat.MessageRole.ASSISTANT) {
            return "澪音";
        }
        if (msg.getSenderName() != null && !msg.getSenderName().isBlank()) {
            return msg.getSenderName();
        }
        return msg.getSenderId() != null ? msg.getSenderId() : "未知用户";
    }

    private int estimateTokens(String text) {
        return text == null ? 0 : text.length() / 4;
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

    private void triggerReflection(String sessionId, String userId, String userRequest,
                                   String agentExecution, String toolsUsed, String response,
                                   String errorSummary) {
        if (!learningBudgetManager.shouldReflect(sessionId, userRequest)) {
            return;
        }
        String executionWithError = (errorSummary != null && !errorSummary.isEmpty())
                ? agentExecution + "\n执行错误: " + errorSummary
                : agentExecution;
        taskEvaluator.evaluate(userRequest, executionWithError, toolsUsed)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(evaluation -> {
                    if (evaluation.isWorthLearning()) {
                        reflectionAgent.reflect(
                                evaluation, userRequest, executionWithError,
                                toolsUsed, sessionId, userId);
                        learningBudgetManager.recordReflection(sessionId);
                        log.info("[Orchestrator] Reflection triggered: session={}, score={}, type={}",
                                sessionId, evaluation.totalScore(), evaluation.learningType());
                    }
                });
    }

    private void recordSkillExecutions(PromptEnricher.EnrichmentResult enrichment,
                                        ExecutionTracker tracker) {
        if (enrichment == null || enrichment.matchedSkills() == null) return;
        for (var skill : enrichment.matchedSkills()) {
            boolean hasFailure = tracker.hasFailures();
            skillLibraryService.recordExecution(skill.getId(), !hasFailure);
        }
        for (var failure : enrichment.matchedFailures()) {
            if (!tracker.hasFailures()) {
                failureLibraryService.markResolved(failure.getId(), null);
            }
        }
        List<Long> skillIds = enrichment.matchedSkills().stream()
                .map(SkillEntity::getId)
                .toList();
        if (skillIds.size() >= 2) {
            skillGraphService.recordCoOccurrences(skillIds);
        }
    }

    /**
     * 构建增强的 System Prompt，包含文件内容、记忆上下文
     */
    private String buildEnrichedPrompt(String systemPrompt, String fileContext, String memoryContext) {
        StringBuilder sb = new StringBuilder();
        sb.append(systemPrompt);

        if (memoryContext != null && !memoryContext.isEmpty()) {
            int memoryTokens = estimateTokens(memoryContext);
            if (memoryTokens > 6000) {
                sb.append("\n\n## 重要记忆（精简）\n");
                sb.append(truncateByTokens(memoryContext, 6000));
            } else {
                sb.append("\n\n").append(memoryContext);
            }
        }

        if (fileContext != null && !fileContext.isEmpty()) {
            sb.append("\n\n## 附加文件内容\n").append(fileContext);
        }

        return sb.toString();
    }

    private String truncateByTokens(String text, int maxTokens) {
        int maxChars = maxTokens * 4;
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars - 3) + "...";
    }
}