package com.mcp.engine.orchestrator;  

import com.mcp.engine.runtime.AgentRuntime;
import com.mcp.engine.runtime.PromptAssemblyResult;
import com.mcp.core.domain.prompt.PromptType;
import com.mcp.core.domain.memory.SkillEntity;
import com.mcp.core.domain.memory.FailureEntity;
import com.mcp.core.service.ChatHistoryService;
import com.mcp.core.service.LongTermMemoryService;
import com.mcp.core.service.PersonaMemoryStore;
import com.mcp.core.service.PromptService;
import com.mcp.engine.agent.Agent;
import com.mcp.engine.agent.ExecutionContext;
import com.mcp.engine.agent.ExecutionTracker;
import com.mcp.engine.agent.LLMRequest;
import com.mcp.engine.agent.card.AgentCard;
import com.mcp.engine.agent.registry.AgentRegistry;
import com.mcp.engine.context.ContextBundle;
import com.mcp.engine.context.TokenBudget;
import com.mcp.engine.context.ContextManager;
import com.mcp.engine.context.ContextRequest;
import com.mcp.common.context.RequestContext;
import com.mcp.common.identity.MemoryIdentity;
import com.mcp.common.identity.IdentityResolver;
import com.mcp.common.identity.UserProfileService;
import com.mcp.common.identity.UserProfile;
import com.mcp.common.identity.UserRole;
import com.mcp.common.identity.GroupContext;
import com.mcp.core.context.BuildContext;
import com.mcp.core.context.PromptPolicy;
import com.mcp.engine.memory.MemoryLifecycleOrchestrator;
import com.mcp.llm.client.ChatMessage;
import com.mcp.llm.client.MessageType;
import java.util.List;
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
import com.mcp.engine.evolution.StrategyEvolutionManager;
import com.mcp.engine.sanitizer.ResponseSanitizer;
import com.mcp.engine.workspace.WorkspaceService;
import com.mcp.common.channel.IntentType;
import com.mcp.common.channel.RecallMode;
import com.mcp.common.channel.ContextRequirement;
import com.mcp.common.channel.WorkingContext;
import com.mcp.common.channel.ActiveContextSource;
import com.mcp.common.channel.SessionState;
import com.mcp.common.workspace.Workspace;
import com.mcp.common.artifact.Artifact;
import com.mcp.common.artifact.ArtifactType;
import com.mcp.common.artifact.ConversationContext;
import com.mcp.engine.artifact.ArtifactService;
import com.mcp.engine.artifact.ReferenceResolver;
import com.mcp.tools.executor.ToolExecutor;
import com.mcp.tools.model.ToolCapability;
import com.mcp.tools.model.ToolExecutionRequest;
import com.mcp.tools.model.ToolQuery;
import com.mcp.tools.model.ToolScore;
import com.mcp.tools.model.ToolDefinition;
import com.mcp.tools.registry.CapabilityResolver;
import com.mcp.tools.registry.ToolRegistry;
import com.mcp.tools.pipeline.PipelineRegistry;
import com.mcp.tools.pipeline.ToolPipeline;
import com.mcp.tools.pipeline.ToolPipelineExecutor;
import com.mcp.tools.pipeline.ToolPipelineResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
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

    private final PromptService promptService;
    private final ChatHistoryService chatHistoryService;
    private final LongTermMemoryService memoryService;
    private final MemoryLifecycleOrchestrator memoryLifecycleOrchestrator;
    private final IdentityResolver identityResolver;
    private final Planner planner;
    private final ContextManager contextManager;
    private final ToolRegistry toolRegistry;
    private final CapabilityResolver capabilityResolver;
    private final PromptEnricher promptEnricher;
    private final TaskEvaluator taskEvaluator;
    private final ReflectionAgent reflectionAgent;
    private final LearningBudgetManager learningBudgetManager;
    private final SkillLibraryService skillLibraryService;
    private final FailureLibraryService failureLibraryService;
    private final SkillGraphService skillGraphService;
    private final WorkspaceService workspaceService;
    private final ArtifactService artifactService;
    private final ReferenceResolver referenceResolver;
    private final ToolExecutor toolExecutor;
    private final OrchestratorPromptService orchestratorPromptService;
    private final ResponseSanitizer responseSanitizer;

    private final UserProfileService userProfileService;

    private final PersonaMemoryStore personaMemoryStore;

    private final StrategyEvolutionManager evolutionManager;

    private final AgentRegistry agentRegistry;

    private final AgentRuntime agentRuntime;

    private final PipelineRegistry pipelineRegistry;

    private final ToolPipelineExecutor pipelineExecutor;

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    private final Map<String, ConversationContext> conversationContexts = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_PLAN_STEPS = 16;

    @Value("${recall.max-history-tokens:5000}")
    private int maxHistoryTokens;

    @Value("${fastpath.max-history-chars:8000}")
    private int maxHistoryChars;

    @Value("${fastpath.max-per-message-chars:600}")
    private int maxPerMessageChars;

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
        MemoryIdentity identity = identityResolver.resolve(sessionId);
        log.info("[Orchestrator] Receive request: {} | Session: {} | Model: {} | UserId: {}",
                request, sessionId, modelConfigId, identity.userId());

        UserProfile userProfile = userProfileService.getUserProfile(identity.userId());
        GroupContext groupContext = identity.groupId() != null
                ? userProfileService.getGroupContext(identity.groupId()) : null;

        return promptService.getCoreSystemPrompt()
                .flatMap(resolvedPrompt -> internalProcess(
                        RequestContext.builder()
                                .identity(identity)
                                .userMessage(request)
                                .modelConfigId(modelConfigId)
                                .userProfile(userProfile)
                                .groupContext(groupContext)
                                .build(),
                        resolvedPrompt));
    }

    @Override
    public Mono<String> processRequestWithSystemPrompt(String request, String sessionId, String systemPrompt, String modelConfigId) {
        if (request == null || request.trim().isEmpty()) {
            return Mono.just("请输入有效的问题。");
        }
        MemoryIdentity identity = identityResolver.resolve(sessionId);
        log.info("[Orchestrator] Receive request: {} | Session: {} | SystemPrompt: {} | Model: {}",
                request, sessionId,
                systemPrompt != null ? systemPrompt.substring(0, Math.min(30, systemPrompt.length())) + "..." : "(using core prompt)",
                modelConfigId != null ? modelConfigId : "default");

        Mono<String> promptMono = (systemPrompt != null && !systemPrompt.isBlank())
                ? Mono.just(systemPrompt)
                : promptService.getCoreSystemPrompt();
        UserProfile userProfile = userProfileService.getUserProfile(identity.userId());
        GroupContext groupContext = identity.groupId() != null
                ? userProfileService.getGroupContext(identity.groupId()) : null;
        return promptMono.flatMap(resolvedPrompt ->
                internalProcess(
                        RequestContext.builder()
                                .identity(identity)
                                .userMessage(request)
                                .modelConfigId(modelConfigId)
                                .userProfile(userProfile)
                                .groupContext(groupContext)
                                .build(),
                        resolvedPrompt));
    }

    @Override
    public Mono<String> processRequestWithIdentity(String request, MemoryIdentity identity, String systemPrompt, String modelConfigId) {
        UserProfile userProfile = userProfileService.getUserProfile(identity.userId());
        GroupContext groupContext = identity.groupId() != null
                ? userProfileService.getGroupContext(identity.groupId()) : null;
        return processRequest(RequestContext.builder()
                .identity(identity)
                .userMessage(request)
                .systemPrompt(systemPrompt)
                .modelConfigId(modelConfigId)
                .userProfile(userProfile)
                .groupContext(groupContext)
                .build());
    }

    @Override
    public Mono<String> processRequest(RequestContext ctx) {
        String request = ctx.getUserMessage();
        if (request == null || request.trim().isEmpty()) {
            return Mono.just("请输入有效的问题。");
        }
        MemoryIdentity identity = ctx.getIdentity();
        log.info("[Orchestrator] Receive request: {} | Session: {} | Platform: {} | UserId: {} | GroupId: {} | Role: {}",
                request, identity.sessionId(), identity.platform(), identity.userId(), identity.groupId(),
                ctx.getUserProfile() != null ? ctx.getUserProfile().getRole() : "N/A");

        Mono<String> promptMono = (ctx.getSystemPrompt() != null && !ctx.getSystemPrompt().isBlank())
                ? Mono.just(ctx.getSystemPrompt())
                : promptService.getCoreSystemPrompt();
        return promptMono.flatMap(resolvedPrompt ->
                internalProcess(ctx, resolvedPrompt));
    }

    /**
     * 统一内部执行入口 — processRequestWithSystemPrompt / processRequestWithModel / processRequestWithIdentity 的共享核心逻辑。
     * 所有 wrapper 方法只需解析身份 + 获取 systemPrompt，然后委托到此方法。
     */
    private Mono<String> internalProcess(RequestContext ctx, String resolvedSystemPrompt) {
        String request = ctx.getUserMessage();
        MemoryIdentity identity = ctx.getIdentity();
        String modelConfigId = ctx.getModelConfigId();
        String sessionId = identity.sessionId();
        long startTime = System.currentTimeMillis();

        log.info("[DIAG-Perf] Request START | session={} | userId={} | message='{}'",
                sessionId, identity.userId(),
                request.length() > 60 ? request.substring(0, 60) + "..." : request);

        UserRole userRole = ctx.getUserProfile() != null ? ctx.getUserProfile().getRole() : null;

        SessionState state = ctx.getSessionState() != null
                ? ctx.getSessionState()
                : new SessionState();
        WorkingContext workingCtx = ctx.getWorkingContext() != null
                ? ctx.getWorkingContext()
                : new WorkingContext();

        ContextRequirement requirement = determineContextRequirement(request, state, workingCtx);
        workingCtx.setLastContextType(requirement);

        long decisionTime = System.currentTimeMillis() - startTime;
        log.info("[Orchestrator] ContextRequirement: {} | session={} | hasActiveDoc={} | isGame={} | source={} | decisionTime={}ms",
                requirement, sessionId, workingCtx.hasActiveDocument(),
                state.isGameMode(), workingCtx.getActiveContextSource(), decisionTime);

        String currentTask = workingCtx.getCurrentTask();
        if (currentTask != null && currentTask.startsWith("SEARCH:")) {
            log.info("[Orchestrator] Detected SEARCH task, routing to SearchAgent: session={}", sessionId);
            return processDocxGenerationWithSearchAgent(ctx, resolvedSystemPrompt, startTime, workingCtx);
        }

        if (currentTask != null && (currentTask.startsWith("DOCX_GENERATION:") || currentTask.startsWith("PPT_GENERATION:"))) {
            log.info("[Orchestrator] Detected {} task, session={}",
                    currentTask.startsWith("DOCX_GENERATION:") ? "DOCX_GENERATION" : "PPT_GENERATION", sessionId);

            // E2: 尝试 Tool Pipeline 快速路径，减少 LLM 轮次
            boolean isDocx = currentTask.startsWith("DOCX_GENERATION:");
            String pipelineId = isDocx ? "search-and-generate-docx" : "search-and-generate-ppt";
            ToolPipeline pipeline = pipelineRegistry.get(pipelineId);
            if (pipeline != null) {
                log.info("[Orchestrator] Pipeline route: {} → {} | session={}", currentTask, pipelineId, sessionId);
                return processWithPipeline(ctx, resolvedSystemPrompt, startTime, workingCtx, pipeline);
            }

            log.info("[Orchestrator] Routing to SearchAgent: session={}", sessionId);
            return processDocxGenerationWithSearchAgent(ctx, resolvedSystemPrompt, startTime, workingCtx);
        }

        return processContextAwareFastPath(ctx, resolvedSystemPrompt, startTime, userRole, requirement, workingCtx);
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

    private Mono<String> preloadFiles(String request, Workspace workspace, String sessionId) {
        return Mono.fromCallable(() -> {
            Set<String> paths = new LinkedHashSet<>();
            Matcher matcher = WINDOWS_PATH_PATTERN.matcher(request);
            while (matcher.find()) {
                String rawPath = matcher.group();
                String cleanPath = rawPath.replaceAll("[，。；！？、\"'<>`]$", "").trim();
                paths.add(cleanPath);
            }

            if (paths.isEmpty()) {
                return buildFollowUpFileContext(request, workspace, sessionId);
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
                                saveOpenedFileToWorkspace(workspace, resolved.toString(), content, resolved, sessionId);
                            }
                        }
                    } else if (Files.isRegularFile(p) && Files.isReadable(p)) {
                        String content = Files.readString(p);
                        sb.append("--- 文件: ").append(filePath).append(" ---\n");
                        sb.append(content).append("\n\n");
                        log.info("[Orchestrator] Preloaded file: {} ({} chars)", filePath, content.length());
                        saveOpenedFileToWorkspace(workspace, filePath, content, p, sessionId);
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

    private void saveOpenedFileToWorkspace(Workspace workspace, String filePath, String content, Path path, String sessionId) {
        if (workspace == null) return;
        try {
            java.time.Instant mtime = Files.getLastModifiedTime(path).toInstant();
            long size = Files.size(path);
            workspace.addOpenedFile(filePath, content, "UTF-8", mtime, size);
            log.info("[Orchestrator] Saved opened file to workspace: {} ({} chars)", filePath, content.length());

            ArtifactType artifactType = detectArtifactType(filePath);
            artifactService.createOrUpdate(
                    sessionId,
                    filePath,
                    artifactType,
                    content,
                    "UTF-8"
            );
            log.info("[Orchestrator] Saved artifact: type={}, path={}, size={}", artifactType, filePath, content.length());
        } catch (IOException e) {
            workspace.addOpenedFile(filePath, content, "UTF-8", null, content.length());
        }
    }

    private ArtifactType detectArtifactType(String filePath) {
        if (filePath == null) return ArtifactType.TEXT;
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".ts")
                || lower.endsWith(".go") || lower.endsWith(".rs") || lower.endsWith(".cpp") || lower.endsWith(".c")) {
            return ArtifactType.CODE;
        }
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return ArtifactType.MARKDOWN;
        if (lower.endsWith(".sql")) return ArtifactType.SQL;
        if (lower.endsWith(".txt")) return ArtifactType.TEXT;
        if (lower.endsWith(".prompt")) return ArtifactType.PROMPT;
        if (lower.endsWith(".diff") || lower.endsWith(".patch")) return ArtifactType.DIFF;
        if (lower.endsWith(".log")) return ArtifactType.LOG;
        if (lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".properties")
                || lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".toml")) {
            return ArtifactType.CONFIG;
        }
        if (lower.endsWith(".pdf")) return ArtifactType.PDF;
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".csv")) return ArtifactType.EXCEL;
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".svg")) {
            return ArtifactType.IMAGE;
        }
        return ArtifactType.FILE;
    }

    private String resolveAndBuildReferenceContext(String request, ConversationContext convCtx, String sessionId) {
        if (!referenceResolver.containsReference(request)) {
            return "";
        }
        Optional<Artifact> resolved = referenceResolver.resolve(request, convCtx, artifactService);
        if (resolved.isEmpty()) {
            return "";
        }
        Artifact artifact = resolved.get();
        log.info("[Orchestrator] Reference resolved: type={}, title={}, id={}",
                artifact.getType(), artifact.getTitle(), artifact.getId());

        StringBuilder sb = new StringBuilder();
        sb.append("【ReferenceResolver — 已解析的引用对象】\n");
        sb.append("用户说\"这个/它/上一份/刚才那个\"时，指的是以下对象：\n\n");
        sb.append("--- Artifact: ");
        if (artifact.getTitle() != null && !artifact.getTitle().isBlank()) {
            sb.append("\"").append(artifact.getTitle()).append("\"");
        } else if (artifact.getPath() != null) {
            sb.append(artifact.getPath());
        } else {
            sb.append(artifact.getId());
        }
        sb.append(" (").append(artifact.getType()).append(")");
        sb.append(" v").append(artifact.getVersion());
        sb.append(" ---\n");
        sb.append(artifact.getContent()).append("\n");
        if (artifact.getModifiedAt() != null) {
            sb.append("(最后修改: ").append(artifact.getModifiedAt()).append(")\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private void trackResponseInConversationContext(String sessionId, String request, String response) {
        try {
            if (shouldSkipArtifactPersistence(response)) {
                log.debug("[Orchestrator] Skipped artifact save (short text): session={}, length={}",
                        sessionId, response != null ? response.length() : 0);
                return;
            }

            ConversationContext convCtx = conversationContexts.computeIfAbsent(
                    sessionId, k -> new ConversationContext(sessionId));

            ArtifactType detectedType = detectResponseArtifactType(request, response);
            Artifact artifact = new Artifact();
            artifact.setType(detectedType);
            artifact.setTitle(generateArtifactTitle(request, response, detectedType));
            artifact.setContent(response);
            artifact.setMimeType(detectMimeType(detectedType));
            artifact.setCreatedBy("agent");
            artifact.addMetadata("request", request);
            artifact.addMetadata("sessionId", sessionId);

            artifactService.saveArtifact(sessionId, artifact);
            convCtx.trackArtifact(artifact);
            log.info("[Orchestrator] ConversationContext updated: session={}, type={}, title={}",
                    sessionId, detectedType, artifact.getTitle());
        } catch (Exception e) {
            log.warn("[Orchestrator] Failed to track response in ConversationContext: {}", e.getMessage());
        }
    }

    private static final int MIN_ARTIFACT_RESPONSE_LENGTH = 200;

    private boolean shouldSkipArtifactPersistence(String response) {
        if (response == null || response.trim().isEmpty()) return true;
        if (response.length() < MIN_ARTIFACT_RESPONSE_LENGTH) return true;
        if (response.contains("```")) return false;
        if (response.contains("**") && response.contains("\n")) return false;
        return false;
    }

    private static final int MIN_MEMORY_LIFECYCLE_REQUEST_LENGTH = 10;
    private static final int MIN_MEMORY_LIFECYCLE_TOTAL_LENGTH = 300;

    private boolean shouldSkipMemoryLifecycle(String request, String response) {
        if (request == null || request.trim().isEmpty()) return true;
        if (request.length() < MIN_MEMORY_LIFECYCLE_REQUEST_LENGTH) return true;
        int totalLen = request.length() + (response != null ? response.length() : 0);
        if (totalLen < MIN_MEMORY_LIFECYCLE_TOTAL_LENGTH) return true;
        return false;
    }

    private void triggerMemoryLifecycle(MemoryIdentity identity, String request, String response) {
        if (shouldSkipMemoryLifecycle(request, response)) {
            log.info("[DIAG-MemoryWrite] 跳过 MemoryLifecycle: request={} chars, response={} chars, total={} chars (低于阈值)",
                    request != null ? request.length() : 0,
                    response != null ? response.length() : 0,
                    (request != null ? request.length() : 0) + (response != null ? response.length() : 0));
            return;
        }
        String conversation = "用户: " + request + "\n助手: " + response;
        log.info("[DIAG-MemoryWrite] 触发 MemoryLifecycle: session={} userId={} request={} chars, response={} chars",
                identity.sessionId(), identity.userId(),
                request != null ? request.length() : 0,
                response != null ? response.length() : 0);
        memoryLifecycleOrchestrator.processMemoryLifecycle(identity, conversation)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        v -> log.info("[DIAG-MemoryWrite] MemoryLifecycle 完成: session={}", identity.sessionId()),
                        error -> log.warn("[DIAG-MemoryWrite] MemoryLifecycle 失败: session={} error={}",
                                identity.sessionId(), error.getMessage())
                );
    }

    private ArtifactType detectResponseArtifactType(String request, String response) {
        if (response == null) return ArtifactType.TEXT;
        String lower = response.trim();
        String reqLower = request != null ? request.toLowerCase() : "";

        if (lower.startsWith("```sql") || lower.contains("SELECT ") || lower.contains("INSERT ")
                || reqLower.contains("sql") || reqLower.contains("查询")) {
            return ArtifactType.SQL;
        }
        if (lower.startsWith("```java") || lower.startsWith("```python") || lower.startsWith("```javascript")
                || lower.startsWith("```go") || lower.startsWith("```rust") || lower.startsWith("```typescript")
                || lower.startsWith("```kotlin") || lower.startsWith("```scala") || lower.startsWith("```c")
                || reqLower.contains("代码") || reqLower.contains("code")) {
            return ArtifactType.CODE;
        }
        if (lower.startsWith("```markdown") || lower.startsWith("```md")
                || (lower.contains("#") && lower.contains("\n"))
                || reqLower.contains("markdown") || reqLower.contains("md")) {
            return ArtifactType.MARKDOWN;
        }
        if (reqLower.contains("报告") || reqLower.contains("report")
                || reqLower.contains("分析") || reqLower.contains("analysis")) {
            return ArtifactType.REPORT;
        }
        if (reqLower.contains("总结") || reqLower.contains("摘要") || reqLower.contains("summary")
                || reqLower.contains("概括")) {
            return ArtifactType.SUMMARY;
        }
        if (reqLower.contains("搜索") || reqLower.contains("search")
                || reqLower.contains("查找") || reqLower.contains("查询")) {
            return ArtifactType.SEARCH_RESULT;
        }
        if (reqLower.contains("prompt") || reqLower.contains("提示")) {
            return ArtifactType.PROMPT;
        }
        return ArtifactType.TEXT;
    }

    private String generateArtifactTitle(String request, String response, ArtifactType type) {
        String extractedTitle = extractTitleFromResponse(response, type);
        if (extractedTitle != null && !extractedTitle.isBlank()) {
            return truncateTitle(extractedTitle);
        }

        if (request != null && !request.isBlank()) {
            String cleaned = cleanRequestTitle(request);
            if (!cleaned.isBlank()) {
                return truncateTitle(cleaned);
            }
        }

        return type.name();
    }

    /**
     * 从响应内容中提取标题。优先从 Markdown 标题行（# Title）提取，
     * 其次从第一行非空文本提取。
     */
    private String extractTitleFromResponse(String response, ArtifactType type) {
        if (response == null || response.isBlank()) return null;
        if (type != ArtifactType.MARKDOWN && type != ArtifactType.REPORT
                && type != ArtifactType.SUMMARY) return null;

        String[] lines = response.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim();
            }
            if (trimmed.startsWith("## ")) {
                return trimmed.substring(3).trim();
            }
        }
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("```") && !trimmed.startsWith("---")) {
                return trimmed;
            }
        }
        return null;
    }

    /**
     * 清理请求文本作为标题：去除系统提示特征（如"你是一位..."等），
     * 只保留用户实际意图。
     */
    private String cleanRequestTitle(String request) {
        if (request == null) return "";
        String cleaned = request.trim();
        if (cleaned.startsWith("你是一位") || cleaned.startsWith("你是")
                || cleaned.startsWith("你是一个")) {
            return "";
        }
        return cleaned;
    }

    private String truncateTitle(String title) {
        if (title.length() > 80) {
            return title.substring(0, 77) + "...";
        }
        return title;
    }

    private String detectMimeType(ArtifactType type) {
        return switch (type) {
            case CODE -> "text/x-code";
            case MARKDOWN -> "text/markdown";
            case SQL -> "text/x-sql";
            case PROMPT -> "text/plain";
            case REPORT, SUMMARY -> "text/markdown";
            case IMAGE -> "image/png";
            case PDF -> "application/pdf";
            case SEARCH_RESULT -> "application/json";
            default -> "text/plain";
        };
    }

    private static final Pattern FOLLOW_UP_REFERENCE_PATTERN =
            Pattern.compile("(这个|那个|它|其|该|上次|刚刚|刚才).*(?:文件|prompt|代码|文档|内容)",
                    Pattern.CASE_INSENSITIVE);

    private String buildFollowUpFileContext(String request, Workspace workspace, String sessionId) {
        if (workspace == null || workspace.getOpenedFiles().isEmpty()) {
            return "";
        }

        boolean isFollowUp = FOLLOW_UP_REFERENCE_PATTERN.matcher(request).find();
        if (!isFollowUp) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【已打开文件内容（自动恢复）】\n");
        sb.append("以下是你上次操作的文件内容，用户说\"这个\"时指的就是这些文件：\n\n");

        String lastActive = workspace.getLastActiveFile();
        if (lastActive != null) {
            Workspace.OpenedFile of = workspace.getOpenedFiles().get(lastActive);
            if (of != null) {
                try {
                    Path p = Path.of(lastActive);
                    if (Files.isRegularFile(p) && Files.isReadable(p)) {
                        String freshContent = Files.readString(p);
                        sb.append("--- 文件: ").append(lastActive).append(" ---\n");
                        sb.append(freshContent).append("\n\n");
                        log.info("[Orchestrator] Follow-up auto-reload: {} ({} chars)", lastActive, freshContent.length());
                        saveOpenedFileToWorkspace(workspace, lastActive, freshContent, p, sessionId);
                        return sb.toString();
                    }
                } catch (IOException e) {
                    log.warn("[Orchestrator] Follow-up reload failed for {}, using cached content", lastActive);
                }
                sb.append("--- 文件: ").append(lastActive).append(" ---\n");
                sb.append(of.getContent()).append("\n\n");
                log.info("[Orchestrator] Follow-up using cached content: {} ({} chars)", lastActive, of.getContent().length());
                return sb.toString();
            }
        }

        for (var entry : workspace.getOpenedFiles().entrySet()) {
            sb.append("--- 文件: ").append(entry.getKey()).append(" ---\n");
            sb.append(entry.getValue().getContent()).append("\n\n");
        }
        log.info("[Orchestrator] Follow-up auto-reload: all {} opened files", workspace.getOpenedFiles().size());
        return sb.toString();
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

                                    return buildRecallHistoryPrompt(request, historyContext, recallMode)
                                        .flatMap(userPrompt -> agentRuntime.run(resolvedPrompt, userPrompt));
                                })
                )
                .map(responseSanitizer::sanitize)
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
    private Mono<String> buildRecallHistoryPrompt(String userRequest, String historyContext, RecallMode recallMode) {
        String templateName = switch (recallMode) {
            case USER_ONLY -> "orchestrator_recall_history_user_only";
            case CONVERSATION -> "orchestrator_recall_history_conversation";
            case BOTH -> "orchestrator_recall_history_both";
        };
        return orchestratorPromptService.render(templateName, Map.of(
                "history_context", historyContext,
                "user_request", userRequest
        ));
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
    public Flux<String> processRequestStream(String request, String sessionId,
                                              String systemPrompt, String modelConfigId) {
        log.info("[Orchestrator] Stream request: {} | Session: {} | Model: {}",
                request, sessionId, modelConfigId != null ? modelConfigId : "default");

        MemoryIdentity identity = identityResolver.resolve(sessionId);
        UserProfile userProfile = userProfileService.getUserProfile(identity.userId());
        GroupContext groupContext = identity.groupId() != null
                ? userProfileService.getGroupContext(identity.groupId()) : null;

        Mono<String> promptMono = (systemPrompt != null && !systemPrompt.isBlank())
                ? Mono.just(systemPrompt)
                : promptService.getCoreSystemPrompt();

        return promptMono.flatMapMany(resolvedPrompt -> {
            BuildContext buildCtx = BuildContext.builder()
                    .baseSystemPrompt(resolvedPrompt)
                    .userMessage(request)
                    .userProfile(userProfile)
                    .groupContext(groupContext)
                    .build();

            PromptAssemblyResult assembly = agentRuntime.assemble(buildCtx, PromptPolicy.CHAT);
            String fullPrompt = assembly.toFullPrompt("", "", "", PromptEnricher.EnrichmentResult.empty());

            if (modelConfigId != null && !modelConfigId.isEmpty()) {
                return agentRuntime.runStreamWithConfig(modelConfigId, fullPrompt, request);
            }
            return agentRuntime.runStream(fullPrompt, request);
        });
    }

    @Override
    public void registerAgent(Agent agent) {
        agents.put(agent.getName(), agent);
        log.info("[Orchestrator] Agent registered: {}", agent.getName());
    }

    private Mono<Agent> resolveBestAgentFromPlan(EditPlan plan) {
        if (plan == null) {
            return resolveBestAgentKeyword("");
        }
        AgentCard.AgentType agentType = planTypeToAgentType(plan.getPlanType());
        List<String> skills = planTypeToSkills(plan.getPlanType());

        if (agentType != null) {
            var matches = agentRegistry.matchByType(agentType);
            if (!matches.isEmpty()) {
                var match = matches.get(0);
                Agent matched = agentRegistry.getAgent(match.agentId()).orElse(null);
                if (matched != null) {
                    log.info("[Orchestrator] Intent-based routing: planType={} → agentType={} → agent={} (skills={})",
                            plan.getPlanType(), agentType, matched.getName(), skills);
                    return Mono.just(matched);
                }
            }
        }

        if (!skills.isEmpty()) {
            return agentRegistry.findBestAgent("", skills)
                    .flatMap(match -> {
                        if (match != null && match.agentId() != null) {
                            Agent matched = agentRegistry.getAgent(match.agentId()).orElse(null);
                            if (matched != null) {
                                log.info("[Orchestrator] Skill-based routing: planType={} → skills={} → agent={} (score={})",
                                        plan.getPlanType(), skills, matched.getName(), match.score());
                                return Mono.just(matched);
                            }
                        }
                        return Mono.empty();
                    });
        }

        log.info("[Orchestrator] No agent matched for planType={}, using default", plan.getPlanType());
        return Mono.justOrEmpty(agents.isEmpty() ? null : agents.values().iterator().next());
    }

    private static final Map<EditPlan.PlanType, AgentCard.AgentType> PLAN_TO_AGENT_TYPE = Map.of(
            EditPlan.PlanType.CHAT, AgentCard.AgentType.CHAT,
            EditPlan.PlanType.READ_ONLY, AgentCard.AgentType.CODE,
            EditPlan.PlanType.CODE_EDIT, AgentCard.AgentType.CODE,
            EditPlan.PlanType.GENERATE, AgentCard.AgentType.GENERAL,
            EditPlan.PlanType.MULTI_STEP, AgentCard.AgentType.EXECUTOR
    );

    private static AgentCard.AgentType planTypeToAgentType(EditPlan.PlanType planType) {
        return PLAN_TO_AGENT_TYPE.getOrDefault(planType, AgentCard.AgentType.GENERAL);
    }

    private static List<String> planTypeToSkills(EditPlan.PlanType planType) {
        return switch (planType) {
            case CHAT -> List.of("chat", "qa");
            case READ_ONLY -> List.of("code-analysis", "code-review", "file-reading");
            case CODE_EDIT -> List.of("code-generation", "code-review", "code-editing", "refactoring");
            case GENERATE -> List.of("code-generation", "content-generation");
            case MULTI_STEP -> List.of("code-generation", "code-analysis", "file-reading", "code-editing");
        };
    }

    private Mono<Agent> resolveBestAgentKeyword(String request) {
        if (agentRegistry.agentCount() > 1) {
            List<String> skills = classifyRequiredSkills(request);
            return agentRegistry.findBestAgent(request, skills)
                    .flatMap(match -> {
                        if (match != null && match.agentId() != null) {
                            Agent matched = agentRegistry.getAgent(match.agentId()).orElse(null);
                            if (matched != null) {
                                log.info("[Orchestrator] Smart routed to agent: {} (score={}, skills={})",
                                        matched.getName(), match.score(), match.matchedSkills());
                                return Mono.just(matched);
                            }
                        }
                        return Mono.empty();
                    })
                    .switchIfEmpty(Mono.justOrEmpty(agents.isEmpty() ? null : agents.values().iterator().next()));
        }
        return Mono.justOrEmpty(agents.isEmpty() ? null : agents.values().iterator().next());
    }

    private List<String> classifyRequiredSkills(String request) {
        if (request == null) return List.of();
        String lower = request.toLowerCase();
        List<String> skills = new ArrayList<>();

        if (lower.contains("代码") || lower.contains("code") || lower.contains("编程")
                || lower.contains("bug") || lower.contains("重构") || lower.contains("refactor")
                || lower.contains("函数") || lower.contains("function") || lower.contains("class")
                || lower.contains("接口") || lower.contains("interface")) {
            skills.add("code-generation");
            skills.add("code-review");
        }

        // P1-1 改进：扩展搜索关键词，覆盖更多搜索意图表达
        if (isSearchRelated(lower)) {
            skills.add("web-search");
            skills.add("information-retrieval");
        }

        if (lower.contains("聊天") || lower.contains("对话") || lower.contains("翻译")
                || lower.contains("翻译") || lower.contains("summary") || lower.contains("总结")) {
            skills.add("chat");
            skills.add("qa");
        }

        return skills.isEmpty() ? List.of("chat") : skills;
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
        return TokenBudget.estimateTokens(text);
    }

    /**
     * 简单意图分类：判断用户请求是否可能需要调用工具
     */
    private boolean likelyNeedsToolsKeyword(String request) {
        if (request == null || request.trim().isEmpty()) {
            return false;
        }
        String lower = request.toLowerCase();
        boolean fileRelated = lower.contains("文件") || lower.contains("路径") || lower.contains("目录")
                || lower.contains("file") || lower.contains("path") || lower.contains("folder")
                || lower.contains("读取") || lower.contains("写入") || lower.contains("编辑")
                || lower.contains("read") || lower.contains("write") || lower.contains("edit");
        return fileRelated || isSearchRelated(lower);
    }

    /**
     * 搜索相关意图检测（classifyRequiredSkills 与 likelyNeedsToolsKeyword 共用）。
     */
    private boolean isSearchRelated(String lower) {
        return lower.contains("搜索") || lower.contains("search") || lower.contains("查找")
                || lower.contains("查询") || lower.contains("最新") || lower.contains("新闻")
                || lower.contains("资料") || lower.contains("信息") || lower.contains("搜")
                || lower.contains("查一下") || lower.contains("搜一下") || lower.contains("找一下")
                || lower.contains("帮我搜") || lower.contains("帮我查") || lower.contains("帮我找")
                || lower.contains("再搜") || lower.contains("重新搜") || lower.contains("搜一次")
                || lower.contains("检索") || lower.contains("搜寻") || lower.contains("搜集")
                || lower.contains("收集") || lower.contains("了解") || lower.contains("调研")
                || lower.contains("研究") || lower.contains("热点") || lower.contains("事件")
                || lower.contains("动态") || lower.contains("报道") || lower.contains("资讯")
                || lower.contains("find") || lower.contains("lookup");
    }

    @Override
    public void registerDefaultTools() {
        List<ToolDefinition> registeredTools = toolRegistry.getAllTools();
        log.info("[Orchestrator] Default tools registration: {} tools registered in ToolRegistry",
                registeredTools.size());
        for (ToolDefinition td : registeredTools) {
            log.debug("[Orchestrator]   Tool: {} (category={}, enabled={})",
                    td.getName(), td.getCategory(), td.isEnabled());
        }

        List<ToolPipeline> pipelines = pipelineRegistry.listAll();
        log.info("[Orchestrator] Default pipelines: {} pipelines registered in PipelineRegistry",
                pipelines.size());
        for (ToolPipeline p : pipelines) {
            log.debug("[Orchestrator]   Pipeline: {} ({}) - {} steps",
                    p.getPipelineId(), p.getName(), p.getSteps() != null ? p.getSteps().size() : 0);
        }

        if (registeredTools.isEmpty()) {
            log.warn("[Orchestrator] No tools registered in ToolRegistry — tool-based agents will fall back to text-only mode");
        }
        if (pipelines.isEmpty()) {
            log.warn("[Orchestrator] No pipelines registered in PipelineRegistry — pipeline optimization disabled");
        }
    }

    private void triggerReflection(String sessionId, String userId, String userRequest,
                                   String agentExecution, List<String> toolsUsed, String response,
                                   String errorSummary, ExecutionTracker tracker) {
        if (!learningBudgetManager.shouldReflect(sessionId, userRequest)) {
            return;
        }
        String executionWithError = (errorSummary != null && !errorSummary.isEmpty())
                ? agentExecution + "\n执行错误: " + errorSummary
                : agentExecution;
        String toolsUsedText = (toolsUsed != null && !toolsUsed.isEmpty())
                ? String.join(", ", toolsUsed)
                : "无";
        boolean hadToolCalls = !tracker.getObservations().isEmpty();
        int toolResultCount = (int) tracker.getObservations().stream()
                .filter(o -> o.success() && o.resultSummary() != null && !o.resultSummary().isEmpty())
                .count();
        boolean hadParseFailure = (errorSummary != null && !errorSummary.isEmpty())
                && (errorSummary.contains("解析失败") || errorSummary.contains("parse error")
                    || errorSummary.contains("unwrap") || errorSummary.contains("extract"));
        taskEvaluator.evaluate(userRequest, executionWithError, toolsUsedText,
                        hadToolCalls, toolResultCount, hadParseFailure)
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
                    recordEvolutionMetrics(sessionId, evaluation, hadToolCalls, tracker);
                });
    }

    private void recordEvolutionMetrics(String sessionId, TaskEvaluator.TaskEvaluation evaluation,
                                         boolean hadToolCalls, ExecutionTracker tracker) {
        boolean success = evaluation.isSuccess();
        double score = evaluation.totalScore();
        double latencyMs = tracker.getTotalElapsedMs();
        boolean toolCallSuccess = hadToolCalls && !tracker.hasFailures();
        boolean skillMatched = !tracker.getObservations().isEmpty()
                && tracker.getObservations().stream().anyMatch(o -> o.success());
        boolean failureAvoided = !tracker.hasParseFailures()
                || (tracker.getObservations().stream().anyMatch(o -> o.success()));

        evolutionManager.recordExecution(sessionId, success, score, latencyMs,
                toolCallSuccess, skillMatched, failureAvoided);
    }

    private void recordSkillExecutions(PromptEnricher.EnrichmentResult enrichment,
                                        ExecutionTracker tracker) {
        if (enrichment == null || enrichment.matchedSkills() == null) return;
        if (tracker.getObservations().isEmpty()) {
            log.debug("[Orchestrator] 无工具调用，跳过 Skill 统计更新");
            return;
        }
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

    private String buildEnrichedPromptWithBudget(String systemPrompt, String fileContext,
                                                  String memoryContext, TokenBudget budget) {
        StringBuilder sb = new StringBuilder();
        sb.append(systemPrompt);

        int systemTokens = TokenBudget.estimateTokens(systemPrompt);
        budget.setSystemPromptTokens(systemTokens);

        if (memoryContext != null && !memoryContext.isEmpty()) {
            int memoryTokens = TokenBudget.estimateTokens(memoryContext);
            int memoryBudget = budget.getMemoryTokens();
            if (memoryTokens > memoryBudget) {
                sb.append("\n\n## 重要记忆（预算: ").append(memoryBudget).append(" tokens, 实际: ")
                        .append(memoryTokens).append(" → 已截断）\n");
                sb.append(truncateByTokens(memoryContext, memoryBudget));
            } else {
                sb.append("\n\n").append(memoryContext);
            }
        }

        if (fileContext != null && !fileContext.isEmpty()) {
            int fileTokens = TokenBudget.estimateTokens(fileContext);
            int fileBudget = budget.getFileContextTokens();
            if (fileTokens > fileBudget) {
                sb.append("\n\n## 附加文件内容（预算: ").append(fileBudget).append(" tokens, 实际: ")
                        .append(fileTokens).append(" → 已截断）\n");
                sb.append(truncateByTokens(fileContext, fileBudget));
            } else {
                sb.append("\n\n## 附加文件内容\n").append(fileContext);
            }
        }

        return sb.toString();
    }

    private void logBudgetUsage(TokenBudget budget, EditPlan.PlanType planType, String fullPrompt) {
        int totalTokens = TokenBudget.estimateTokens(fullPrompt);
        int remaining = budget.remaining();
        double usagePercent = budget.getTotalBudget() > 0
                ? (double) totalTokens / budget.getTotalBudget() * 100 : 0;

        log.info("[Orchestrator] Context budget: planType={}, total={}, used={}, remaining={}, usage={}%",
                planType, budget.getTotalBudget(), totalTokens, remaining,
                String.format("%.1f", usagePercent));

        if (totalTokens > budget.getTotalBudget()) {
            log.warn("[Orchestrator] Context budget exceeded! total={}, used={}, overflow={}",
                    budget.getTotalBudget(), totalTokens, totalTokens - budget.getTotalBudget());
        }
    }

    private String truncateByTokens(String text, int maxTokens) {
        int maxChars = maxTokens * 4;
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars - 3) + "...";
    }

    // ==================== P1: Plan-Driven Execution Loop ====================

    private record StepObservation(
            com.mcp.engine.planner.PlanStep step,
            boolean success,
            String result,
            String error,
            long durationMs,
            boolean fallbackAttempted,
            String fallbackTool,
            boolean fallbackSuccess
    ) {
        StepObservation(com.mcp.engine.planner.PlanStep step, boolean success,
                        String result, String error, long durationMs) {
            this(step, success, result, error, durationMs, false, null, false);
        }
    }

    private Mono<String> executePlanLoop(EditPlan plan, String request, String systemPrompt,
                                          String memoryContext, ExecutionTracker tracker,
                                          String sessionId,
                                          PromptEnricher.EnrichmentResult enrichment) {
        List<com.mcp.engine.planner.PlanStep> steps = plan.getSteps();
        if (steps == null || steps.isEmpty()) {
            log.warn("[Orchestrator] Plan has no steps, falling back");
            return Mono.just("计划中没有可执行的步骤。");
        }

        List<StepObservation> observations = new ArrayList<>();
        ExecutionContext ctx = ExecutionContext.create(
                sessionId,
                plan.getIntent() != null ? plan.getIntent() : request,
                plan,
                MAX_PLAN_STEPS);
        ctx.setCurrentHypothesis(plan.getReasoning());
        log.info("[Orchestrator] ExecutionContext created: goal='{}', planType={}, steps={}",
                ctx.getGoal(), plan.getPlanType(), steps.size());

        List<SkillEntity> matchedSkills = enrichment != null ? enrichment.matchedSkills() : List.of();
        return executeStep(0, new ArrayList<>(steps), observations, ctx, request,
                        systemPrompt, memoryContext, tracker, plan, matchedSkills)
                .doOnSuccess(response -> {
                    log.info("[Orchestrator] Plan execution complete: {} steps, {} completed, {} failed",
                            steps.size(), ctx.totalCompletedCount(), ctx.totalFailedCount());
                    recordSkillExecutions(enrichment, tracker);
                    triggerReflection(sessionId, null, request,
                            ctx.buildReflectionSummary(),
                            tracker.buildToolsUsedList(), response,
                            tracker.buildErrorSummary(), tracker);
                })
                .doOnError(error -> {
                    log.error("[Orchestrator] Plan execution failed: {}", error.getMessage(), error);
                });
    }

    private Mono<String> executeStep(int index, List<com.mcp.engine.planner.PlanStep> steps,
                                      List<StepObservation> observations,
                                      ExecutionContext ctx,
                                      String request, String systemPrompt, String memoryContext,
                                      ExecutionTracker tracker, EditPlan plan,
                                      List<SkillEntity> matchedSkills) {
        if (index >= steps.size() || index >= MAX_PLAN_STEPS) {
            log.info("[Orchestrator] All {} steps executed, generating final answer", observations.size());
            return generateFinalAnswer(request, systemPrompt, observations, plan);
        }

        com.mcp.engine.planner.PlanStep step = steps.get(index);
        long startTime = System.currentTimeMillis();

        String effectiveToolName = resolveEffectiveToolName(step);
        if (effectiveToolName == null) {
            log.warn("[Orchestrator] Step {}/{}: No tool resolved for capability={}, toolName={}, skipping",
                    index + 1, steps.size(), step.getCapability(), step.getToolName());
            return executeStep(index + 1, steps, observations, ctx,
                    request, systemPrompt, memoryContext, tracker, plan, matchedSkills);
        }

        log.info("[Orchestrator] Executing step {}/{}: {} ({})",
                index + 1, steps.size(), effectiveToolName, step.getReason());

        String keyArg = step.getArguments() != null && !step.getArguments().isEmpty()
                ? step.getArguments().values().iterator().next().toString() : null;
        if (ctx.hasAlreadyFailed(effectiveToolName, keyArg)) {
            log.warn("[Orchestrator] Step {}/{}: {} already failed in this execution, skipping",
                    index + 1, steps.size(), effectiveToolName);
            ctx.addObservation("跳过已失败步骤: " + effectiveToolName + (keyArg != null ? "(" + keyArg + ")" : ""));
            return executeStep(index + 1, steps, observations, ctx,
                    request, systemPrompt, memoryContext, tracker, plan, matchedSkills);
        }

        Map<String, Object> mergedArgs = new java.util.HashMap<>(
                step.getArguments() != null ? step.getArguments() : java.util.Collections.emptyMap());

        var guidance = skillLibraryService.buildStepGuidance(matchedSkills, effectiveToolName);
        if (guidance.isPresent()) {
            var g = guidance.get();
            log.info("[Orchestrator] Applying skill guidance: skill='{}' (成功率={}%) → tool='{}' params={}",
                    g.skillName(), String.format("%.0f", g.successRate()), effectiveToolName,
                    g.suggestedParams().keySet());

            for (var entry : g.suggestedParams().entrySet()) {
                mergedArgs.putIfAbsent(entry.getKey(), entry.getValue());
            }

            if (g.fallbackTool() != null && !g.fallbackTool().isBlank()) {
                log.info("[Orchestrator] Skill fallback tool registered: {}", g.fallbackTool());
            }
        }

        ToolExecutionRequest execRequest = new ToolExecutionRequest();
        execRequest.setToolName(effectiveToolName);
        execRequest.setArguments(mergedArgs);

        return toolExecutor.execute(execRequest)
                .flatMap(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    String resultStr = result != null ? result.toString() : "";
                    StepObservation obs = new StepObservation(step, true, resultStr, null, duration);
                    observations.add(obs);
                    tracker.recordToolCall(effectiveToolName,
                            toJson(step.getArguments()), true,
                            truncate(resultStr, 200), null, duration);
                    ctx.recordStepSuccess(step, truncate(resultStr, 200), duration);
                    ctx.recordToolCall(effectiveToolName, step.getArguments(),
                            true, truncate(resultStr, 200), null, duration);
                    log.info("[Orchestrator] Step {}/{} success: {} ({}ms, {} chars)",
                            index + 1, steps.size(), effectiveToolName, duration,
                            resultStr.length());
                    return Mono.just(obs);
                })
                .onErrorResume(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    String errMsg = error.getMessage() != null ? error.getMessage() : "Unknown error";
                    log.warn("[Orchestrator] Step {}/{} failed: {} ({}ms, error: {}), attempting recovery",
                            index + 1, steps.size(), effectiveToolName, duration, errMsg);

                    ctx.recordStepFailure(step, errMsg, duration, 1);
                    ctx.recordToolCall(effectiveToolName, step.getArguments(),
                            false, null, errMsg, duration);

                    return recoverFromFailure(step, effectiveToolName, mergedArgs, startTime, guidance, errMsg,
                            index, steps.size(), tracker, observations);
                })
                .flatMap(obs -> executeStep(index + 1, steps, observations, ctx,
                        request, systemPrompt, memoryContext, tracker, plan, matchedSkills));
    }

    private String resolveEffectiveToolName(com.mcp.engine.planner.PlanStep step) {
        ToolCapability capability = step.getCapability();
        String toolName = step.getToolName();

        if (capability != null && (toolName == null || toolName.isBlank())) {
            List<ToolScore> ranked = capabilityResolver.resolveRanked(ToolQuery.builder()
                    .capability(capability)
                    .enabled(true)
                    .build());
            if (!ranked.isEmpty()) {
                ToolScore best = ranked.get(0);
                log.info("[Orchestrator] Capability {} → best tool: {} (score={}, skillBonus={}, failurePenalty={})",
                        capability, best.getToolName(),
                        String.format("%.1f", best.getCompositeScore()),
                        String.format("%.1f", best.getSkillBonus()),
                        String.format("%.1f", best.getFailurePenalty()));
                return best.getToolName();
            }
            log.warn("[Orchestrator] No tool found for capability: {}", capability);
            return null;
        }

        return toolName;
    }

    private Mono<StepObservation> recoverFromFailure(
            com.mcp.engine.planner.PlanStep step,
            String effectiveToolName,
            Map<String, Object> mergedArgs,
            long startTime,
            Optional<SkillLibraryService.SkillStepGuidance> guidance,
            String errMsg,
            int index, int totalSteps,
            ExecutionTracker tracker,
            List<StepObservation> observations) {

        long duration = System.currentTimeMillis() - startTime;

        if (guidance.isPresent() && guidance.get().fallbackTool() != null
                && !guidance.get().fallbackTool().isBlank()) {
            String fallbackTool = guidance.get().fallbackTool();
            log.info("[Orchestrator] Recovery: executing fallback tool '{}' for failed step '{}'",
                    fallbackTool, effectiveToolName);

            ToolExecutionRequest fallbackRequest = new ToolExecutionRequest();
            fallbackRequest.setToolName(fallbackTool);
            fallbackRequest.setArguments(mergedArgs);

            long fallbackStart = System.currentTimeMillis();
            return toolExecutor.execute(fallbackRequest)
                    .map(fallbackResult -> {
                        long fallbackDuration = System.currentTimeMillis() - fallbackStart;
                        String resultStr = fallbackResult != null ? fallbackResult.toString() : "";
                        StepObservation obs = new StepObservation(step, true, resultStr, null,
                                duration + fallbackDuration, true, fallbackTool, true);
                        observations.add(obs);
                        tracker.recordToolCall(fallbackTool,
                                toJson(mergedArgs), true,
                                truncate(resultStr, 200), null, fallbackDuration);
                        log.info("[Orchestrator] Recovery: fallback tool '{}' succeeded ({}ms)",
                                fallbackTool, fallbackDuration);
                        return obs;
                    })
                    .onErrorResume(fallbackError -> {
                        long fallbackDuration = System.currentTimeMillis() - fallbackStart;
                        String fallbackErr = fallbackError.getMessage() != null
                                ? fallbackError.getMessage() : "Unknown fallback error";
                        log.warn("[Orchestrator] Recovery: fallback tool '{}' also failed: {}",
                                fallbackTool, fallbackErr);

                        return tryFailureLibraryRecovery(step, effectiveToolName, mergedArgs, startTime, errMsg,
                                fallbackTool, fallbackErr, duration, tracker, observations);
                    });
        }

        return tryFailureLibraryRecovery(step, effectiveToolName, mergedArgs, startTime, errMsg,
                null, null, duration, tracker, observations);
    }

    private Mono<StepObservation> tryFailureLibraryRecovery(
            com.mcp.engine.planner.PlanStep step,
            String effectiveToolName,
            Map<String, Object> mergedArgs,
            long startTime,
            String originalError,
            String fallbackTool,
            String fallbackError,
            long duration,
            ExecutionTracker tracker,
            List<StepObservation> observations) {

        List<FailureEntity> unresolvedFailures = failureLibraryService.getUnresolvedFailures();
        if (unresolvedFailures.isEmpty()) {
            return recordFinalFailure(step, effectiveToolName, mergedArgs, startTime, originalError,
                    fallbackTool, fallbackError, duration, tracker, observations);
        }

        String errorToMatch = fallbackError != null ? fallbackError : originalError;
        for (FailureEntity failure : unresolvedFailures) {
            if (failure.getErrorSignature() != null
                    && errorToMatch.toLowerCase().contains(failure.getErrorSignature().toLowerCase())) {

                log.info("[Orchestrator] Recovery: FailureLibrary match found: pattern='{}', correct='{}'",
                        failure.getTaskPattern(), failure.getCorrectApproach());

                if (failure.getCorrectApproach() != null && !failure.getCorrectApproach().isBlank()) {
                    log.info("[Orchestrator] Recovery: applying correct approach from FailureLibrary: {}",
                            failure.getCorrectApproach());

                    try {
                        String correctJson = failure.getCorrectApproach();
                        if (correctJson.contains("{")) {
                            int start = correctJson.indexOf("{");
                            int end = correctJson.lastIndexOf("}") + 1;
                            String jsonPart = correctJson.substring(start, end);
                            @SuppressWarnings("unchecked")
                            Map<String, Object> correctionParams = objectMapper.readValue(
                                    jsonPart, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                            for (var entry : correctionParams.entrySet()) {
                                mergedArgs.putIfAbsent(entry.getKey(), entry.getValue());
                            }
                        }
                    } catch (Exception e) {
                        log.debug("[Orchestrator] Recovery: failed to parse correctApproach JSON: {}", e.getMessage());
                    }

                    long retryStart = System.currentTimeMillis();
                    ToolExecutionRequest retryRequest = new ToolExecutionRequest();
                    retryRequest.setToolName(effectiveToolName);
                    retryRequest.setArguments(mergedArgs);

                    return toolExecutor.execute(retryRequest)
                            .map(retryResult -> {
                                long retryDuration = System.currentTimeMillis() - retryStart;
                                String resultStr = retryResult != null ? retryResult.toString() : "";
                                StepObservation obs = new StepObservation(step, true, resultStr, null,
                                        duration + retryDuration, true,
                                        "_failure_correction", true);
                                observations.add(obs);
                                tracker.recordToolCall(effectiveToolName + "#retry",
                                        toJson(mergedArgs), true,
                                        truncate(resultStr, 200), null, retryDuration);
                                log.info("[Orchestrator] Recovery: FailureLibrary correction succeeded ({}ms)",
                                        retryDuration);
                                return obs;
                            })
                            .onErrorResume(retryError -> {
                                long retryDuration = System.currentTimeMillis() - retryStart;
                                String retryErr = retryError.getMessage() != null
                                        ? retryError.getMessage() : "Unknown retry error";
                                log.warn("[Orchestrator] Recovery: FailureLibrary correction also failed: {}",
                                        retryErr);
                                return recordFinalFailure(step, effectiveToolName, mergedArgs, startTime, originalError,
                                        fallbackTool, fallbackError, duration + retryDuration,
                                        tracker, observations);
                            });
                }
            }
        }

        return recordFinalFailure(step, effectiveToolName, mergedArgs, startTime, originalError,
                fallbackTool, fallbackError, duration, tracker, observations);
    }

    private Mono<StepObservation> recordFinalFailure(
            com.mcp.engine.planner.PlanStep step,
            String effectiveToolName,
            Map<String, Object> mergedArgs,
            long startTime,
            String originalError,
            String fallbackTool,
            String fallbackError,
            long duration,
            ExecutionTracker tracker,
            List<StepObservation> observations) {

        String finalError = fallbackError != null
                ? "原始错误: " + originalError + " | 恢复错误: " + fallbackError
                : originalError;

        StepObservation obs = new StepObservation(step, false, "", finalError, duration,
                fallbackTool != null, fallbackTool, false);
        observations.add(obs);
        tracker.recordToolCall(effectiveToolName,
                toJson(mergedArgs), false,
                "", finalError, duration);
        log.warn("[Orchestrator] Recovery: all recovery attempts failed for step '{}': {}",
                effectiveToolName, finalError);
        return Mono.just(obs);
    }

    private Mono<String> generateFinalAnswer(String request, String systemPrompt,
                                              List<StepObservation> observations,
                                              EditPlan plan) {
        StringBuilder obsContext = new StringBuilder(4096);
        obsContext.append("## 任务：").append(plan.getIntent()).append("\n");
        obsContext.append("规划推理：").append(plan.getReasoning()).append("\n\n");
        obsContext.append("## 工具执行结果：\n\n");

        long successCount = observations.stream().filter(StepObservation::success).count();
        long failCount = observations.size() - successCount;

        for (int i = 0; i < observations.size(); i++) {
            StepObservation obs = observations.get(i);
            obsContext.append("### 步骤 ").append(i + 1).append("：")
                    .append(obs.step().getDisplayName()).append("\n");
            obsContext.append("原因：").append(obs.step().getReason()).append("\n");
            if (obs.fallbackAttempted()) {
                obsContext.append("恢复：尝试了回退策略");
                if (obs.fallbackTool() != null) {
                    obsContext.append(" → ").append(obs.fallbackTool());
                }
                obsContext.append(" (").append(obs.fallbackSuccess() ? "成功" : "失败").append(")\n");
            }
            if (obs.success()) {
                String truncated = obs.result().length() > 8000
                        ? obs.result().substring(0, 8000) + "\n...(结果已截断)"
                        : obs.result();
                obsContext.append("结果：").append(truncated).append("\n\n");
            } else {
                obsContext.append("错误：").append(obs.error()).append("\n\n");
            }
        }

        obsContext.append("## 统计\n");
        obsContext.append("总步骤：").append(observations.size())
                .append(" | 成功：").append(successCount)
                .append(" | 失败：").append(failCount).append("\n\n");

        return orchestratorPromptService.render(
                "orchestrator_final_answer",
                Map.of(
                        "observations_context", obsContext.toString(),
                        "user_request", request
                ))
                .flatMap(finalAnswerPrompt -> {
                    String finalPrompt = systemPrompt + "\n\n" + finalAnswerPrompt;
                    return orchestratorPromptService.render(
                            "orchestrator_user_prompt_prefix",
                            Map.of("user_message", request))
                            .flatMap(finalUserPrompt -> {
                                int finalPromptTokens = TokenBudget.estimateTokens(finalPrompt);
                                int finalUserTokens = TokenBudget.estimateTokens(finalUserPrompt);
                                log.info("[Orchestrator] Generating final answer: {} observations ({} success, {} fail), "
                                                + "prompt tokens={} (system={} + answer={}), user tokens={}",
                                        observations.size(), successCount, failCount,
                                        finalPromptTokens + finalUserTokens,
                                        finalPromptTokens, finalUserTokens);
                                return agentRuntime.run(finalPrompt, finalUserPrompt);
                            });
                });
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }

    // ==================== P0: Context-Aware Loading Architecture ====================

    /**
     * 状态驱动的上下文需求判断。
     * 核心原则：90% 依赖 SessionState + WorkingContext，10% 依赖消息内容。
     * 不负责决定是否走 FULL Pipeline（FULL 由 Intent/Planner 决定）。
     * <p>
     * 级别递减优先级（GAME > DOCUMENT > WORKSPACE > CONVERSATION > NONE）：
     * <ul>
     *   <li>GAME 模式 → DOCUMENT：游戏场景需要完整故事/角色上下文</li>
     *   <li>WorkingContext.needsDocumentContext() → DOCUMENT：有活跃文档/artifact 需要召回</li>
     *   <li>containsFileOperation() → WORKSPACE：需要加载项目文件（如代码、配置）</li>
     *   <li>消息长度 > 20 且非简单问候 → CONVERSATION：普通聊天，需要历史上下文</li>
     *   <li>简单问候 → NONE：最轻量级，无需任何额外上下文</li>
     * </ul>
     */
    private ContextRequirement determineContextRequirement(
            String request, SessionState state, WorkingContext workingCtx) {

        if (state.isGameMode()) {
            log.info("[DIAG-ContextRequirement] → DOCUMENT | reason=GameMode active");
            return ContextRequirement.DOCUMENT;
        }

        if (workingCtx.needsDocumentContext()) {
            log.info("[DIAG-ContextRequirement] → DOCUMENT | reason=WorkingContext.needsDocumentContext | activeDoc={} | activeArtifact={}",
                    workingCtx.getActiveDocumentPath(), workingCtx.getActiveArtifactId());
            return ContextRequirement.DOCUMENT;
        }

        String trimmed = request.trim();
        String lower = trimmed.toLowerCase();

        if (containsFileOperation(lower)) {
            log.info("[DIAG-ContextRequirement] → WORKSPACE | reason=containsFileOperation | message='{}'",
                    request.length() > 50 ? request.substring(0, 50) + "..." : request);
            return ContextRequirement.WORKSPACE;
        }

        if (isShortGreeting(trimmed, lower)) {
            log.info("[DIAG-ContextRequirement] → NONE | reason=shortGreeting | length={} | message='{}'",
                    trimmed.length(), trimmed);
            return ContextRequirement.NONE;
        }

        if (isSearchRelated(lower) && !containsFileOperation(lower)) {
            log.info("[DIAG-ContextRequirement] → SEARCH | reason=isSearchRelated | message='{}'",
                    request.length() > 50 ? request.substring(0, 50) + "..." : request);
            return ContextRequirement.SEARCH;
        }

        log.info("[DIAG-ContextRequirement] → CONVERSATION | reason=default fallthrough | isGame={} | hasActiveDoc={} | hasActiveArtifact={} | message='{}'",
                state.isGameMode(), workingCtx.hasActiveDocument(),
                workingCtx.getActiveArtifactId() != null,
                request.length() > 80 ? request.substring(0, 80) + "..." : request);
        return ContextRequirement.CONVERSATION;
    }

    private boolean containsFileOperation(String lower) {
        if (lower.contains(":\\")) {
            return true;
        }
        return lower.contains("读取文件") || lower.contains("打开文件")
                || lower.contains("代码文件") || lower.contains("源文件")
                || lower.contains("文件路径") || lower.contains("工作区")
                || lower.contains("项目文件") || lower.contains("工程文件");
    }

    private boolean isShortGreeting(String trimmed, String lower) {
        if (trimmed.length() > 20) return false;
        return lower.matches(
                "^(你好|hi|hello|嗨|在吗|在不在|早上好|下午好|晚上好|"
                + "早安|晚安|谢谢|多谢|感谢|不客气|再见|拜拜|bye|"
                + "哈哈|嘿嘿|呵呵|嗯|哦|好的|ok|okay|"
                + "你是谁|你叫什么|你的名字|你会什么|你能做什么|你有什么功能|"
                + "你是谁\\?|你叫什么\\?|你的名字\\?)$");
    }

    private String extractFirstFilePath(String request) {
        Matcher matcher = WINDOWS_PATH_PATTERN.matcher(request);
        while (matcher.find()) {
            String rawPath = matcher.group();
            return rawPath.replaceAll("[，。；！？、\"'<>`]$", "").trim();
        }
        Matcher fnMatcher = FILENAME_PATTERN.matcher(request);
        if (fnMatcher.find()) {
            return fnMatcher.group();
        }
        return null;
    }

    /**
     * 从 artifactContext 的召回结果中提取【文档摘要】部分，缓存到 WorkingContext。
     * 后续请求复用摘要，避免重复生成。
     */
    private void cacheArtifactSummary(WorkingContext workingCtx, String artifactContext) {
        if (workingCtx.getActiveDocumentSummary() != null) {
            return;
        }
        int summaryStart = artifactContext.indexOf("【文档摘要】");
        if (summaryStart < 0) {
            return;
        }
        int contentStart = artifactContext.indexOf('\n', summaryStart);
        if (contentStart < 0) {
            return;
        }
        int chunkStart = artifactContext.indexOf("【相关段落】", contentStart);
        String summary = chunkStart > contentStart
                ? artifactContext.substring(contentStart + 1, chunkStart).trim()
                : artifactContext.substring(contentStart + 1).trim();
        if (!summary.isEmpty()) {
            workingCtx.setActiveDocumentSummary(summary);
            log.info("[Orchestrator] Document summary cached, chars={}", summary.length());
        }
    }

    /**
     * Context-Aware FastPath — 根据 ContextRequirement 分级加载。
     * 不是"跳过所有"，而是"按需加载"。
     * <p>
     * PromptPolicy 设计意图：
     * <ul>
     *   <li>NONE → CHAT_LIGHT：轻量级 system prompt，不含任务/文档/文件上下文，适合简单问候</li>
     *   <li>CONVERSATION → CHAT_LIGHT + 聊天历史：有历史但无任务上下文，单条消息 ≤600 chars</li>
     *   <li>DOCUMENT → CHAT（非GAME）/ GAME：有文档/artifact 上下文，需要完整 system prompt</li>
     *   <li>WORKSPACE → CHAT：有 workspace 文件上下文，需要完整 system prompt + 文件预加载</li>
     * </ul>
     */
    private Mono<String> processContextAwareFastPath(
            RequestContext ctx, String resolvedSystemPrompt,
            long startTime, UserRole userRole,
            ContextRequirement requirement, WorkingContext workingCtx) {

        String request = ctx.getUserMessage();
        MemoryIdentity identity = ctx.getIdentity();
        String modelConfigId = ctx.getModelConfigId();
        String sessionId = identity.sessionId();

        log.info("[Orchestrator] FastPath: requirement={}, hasActiveDoc={}, isGame={}, source={}, message='{}'",
                requirement, workingCtx.hasActiveDocument(),
                ctx.getSessionState() != null && ctx.getSessionState().isGameMode(),
                workingCtx.getActiveContextSource(), request);

        return switch (requirement) {
            case NONE -> processFastPathNone(ctx, resolvedSystemPrompt, startTime, userRole, workingCtx);
            case CONVERSATION -> processFastPathConversation(ctx, resolvedSystemPrompt, startTime, userRole, workingCtx);
            case DOCUMENT -> processFastPathDocument(ctx, resolvedSystemPrompt, startTime, userRole, workingCtx);
            case WORKSPACE -> processFastPathWorkspace(ctx, resolvedSystemPrompt, startTime, userRole, workingCtx);
            case SEARCH -> processFastPathSearch(ctx, resolvedSystemPrompt, startTime, userRole, workingCtx);
        };

    }

    /**
     * E2: Tool Pipeline 快速路径 — 使用预定义管道执行工具链，减少 LLM 轮次。
     * <p>
     * 与 SearchAgent ReAct 循环的区别：
     * <ul>
     *   <li>Pipeline: 确定性执行 multi_search → generate_docx/ppt，0 次 LLM 工具决策</li>
     *   <li>SearchAgent: LLM 决策每一步工具调用，通常需要 2-3 轮 LLM 交互</li>
     * </ul>
     * 适用场景：搜索+生成文档/PPT 的标准工作流，内容格式可预测。
     */
    private Mono<String> processWithPipeline(
            RequestContext ctx, String resolvedSystemPrompt,
            long startTime, WorkingContext workingCtx, ToolPipeline pipeline) {
        String request = ctx.getUserMessage();
        MemoryIdentity identity = ctx.getIdentity();
        String sessionId = identity.sessionId();

        log.info("[Orchestrator] Pipeline[{}] executing: {} steps | session={}",
                pipeline.getPipelineId(), pipeline.getSteps().size(), sessionId);

        ToolPipeline finalPipeline = ToolPipeline.builder()
                .pipelineId(pipeline.getPipelineId())
                .name(pipeline.getName())
                .steps(pipeline.getSteps())
                .timeoutSeconds(pipeline.getTimeoutSeconds())
                .build();

        return pipelineExecutor.execute(finalPipeline)
                .doOnSuccess(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    String outcome = result.isSuccess() ? "SUCCESS" : "FAILED";
                    log.info("[Orchestrator] Pipeline[{}] {}: duration={}ms | session={} | steps={}",
                            pipeline.getPipelineId(), outcome, duration, sessionId,
                            result.getStepResults() != null ? result.getStepResults().size() : 0);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("[Orchestrator] Pipeline[{}] ERROR: duration={}ms | session={} | error={}",
                            pipeline.getPipelineId(), duration, sessionId, error.getMessage());
                })
                .flatMap(result -> {
                    if (result.isSuccess()) {
                        String response = result.getFinalOutput() != null
                                ? result.getFinalOutput().toString()
                                : "Pipeline completed successfully.";
                        return chatHistoryService.saveUserAndAssistantMessage(identity, request, response)
                                .thenReturn(response);
                    }
                    log.warn("[Orchestrator] Pipeline[{}] failed, falling back to SearchAgent: session={}",
                            pipeline.getPipelineId(), sessionId);
                    return processDocxGenerationWithSearchAgent(ctx, resolvedSystemPrompt, startTime, workingCtx);
                })
                .onErrorResume(ex -> {
                    log.warn("[Orchestrator] Pipeline[{}] exception, falling back to SearchAgent: session={} | error={}",
                            pipeline.getPipelineId(), sessionId, ex.getMessage());
                    return processDocxGenerationWithSearchAgent(ctx, resolvedSystemPrompt, startTime, workingCtx);
                });
    }

    private Mono<String> processDocxGenerationWithSearchAgent(
            RequestContext ctx, String resolvedSystemPrompt,
            long startTime, WorkingContext workingCtx) {
        String request = ctx.getUserMessage();
        MemoryIdentity identity = ctx.getIdentity();
        String sessionId = identity.sessionId();

        return Mono.justOrEmpty(agentRegistry.getAgent("search-agent"))
                .switchIfEmpty(resolveBestAgentKeyword(request)
                        .doOnNext(agent -> log.info(
                                "[Orchestrator] SearchAgent not in registry, fallback keyword routing to: {} | session={}",
                                agent.getName(), sessionId)))
                .flatMap(searchAgent -> {
                    log.info("[Orchestrator] Routing DOCX_GENERATION to agent: {} | session={}",
                            searchAgent.getName(), sessionId);

                    LLMRequest llmRequest = LLMRequest.builder()
                            .sessionId(sessionId)
                            .userId(identity.userId())
                            .groupId(identity.groupId())
                            .systemPrompt(resolvedSystemPrompt)
                            .userMessage(request)
                            .modelConfigId(ctx.getModelConfigId())
                            .build();

                    return searchAgent.execute(llmRequest)
                            .doOnSuccess(response -> {
                                long duration = System.currentTimeMillis() - startTime;
                                log.info("[Orchestrator] DOCX_GENERATION via SearchAgent success! Duration: {}ms | Session: {} | responseLen={}",
                                        duration, sessionId, response != null ? response.length() : 0);
                                triggerMemoryLifecycle(identity, request, response);
                            })
                            .doOnError(error -> log.error(
                                    "[Orchestrator] DOCX_GENERATION via SearchAgent failed: session={} error={}",
                                    sessionId, error.getMessage(), error));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("[Orchestrator] No SearchAgent found for DOCX_GENERATION, falling back to FastPath: session={}",
                            sessionId);
                    return processContextAwareFastPath(ctx, resolvedSystemPrompt, startTime,
                            ctx.getUserProfile() != null ? ctx.getUserProfile().getRole() : null,
                            ContextRequirement.DOCUMENT, workingCtx);
                }));
    }

    private Mono<String> processFastPathNone(RequestContext ctx, String resolvedSystemPrompt,
                                              long startTime, UserRole userRole,
                                              WorkingContext workingCtx) {
        String request = ctx.getUserMessage();
        MemoryIdentity identity = ctx.getIdentity();
        String modelConfigId = ctx.getModelConfigId();
        String sessionId = identity.sessionId();

        log.info("[Orchestrator] FastPath[NONE]: lightweight chat for '{}'", request);

        long memStart = System.currentTimeMillis();
        return memoryService.buildWorkingContext(identity, userRole)
                .doOnSuccess(memCtx -> {
                    long memElapsed = System.currentTimeMillis() - memStart;
                    log.info("[DIAG-Perf] FastPath[NONE] memory loading: {}ms | memCtxLen={}",
                            memElapsed, memCtx != null ? memCtx.length() : 0);
                })
                .flatMap(memoryContext -> {
                    BuildContext buildCtx = BuildContext.builder()
                            .baseSystemPrompt(resolvedSystemPrompt)
                            .personaPrompt(personaMemoryStore.getPersonaMemoryText())
                            .userMessage(request)
                            .userProfile(ctx.getUserProfile())
                            .groupContext(ctx.getGroupContext())
                            .state(ctx.getSessionState())
                            .workingContext(workingCtx)
                            .build();

                    PromptAssemblyResult assembly = agentRuntime.assemble(buildCtx, PromptPolicy.CHAT_LIGHT);
                    String fullPrompt = assembly.toFullPrompt(memoryContext,
                            "", "", PromptEnricher.EnrichmentResult.empty());

                    String userPrompt = buildUserPrompt(request, "");
                    if (modelConfigId != null && !modelConfigId.isEmpty()) {
                        return agentRuntime.runWithConfig(modelConfigId, fullPrompt, userPrompt);
                    }
                    return agentRuntime.run(fullPrompt, userPrompt);
                })
                .map(responseSanitizer::sanitize)
                .flatMap(response ->
                        chatHistoryService.saveUserAndAssistantMessage(identity, request, response)
                                .thenReturn(response)
                )
                .doOnSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("[Orchestrator] FastPath[NONE] success! Duration: {}ms | Session: {}",
                            duration, sessionId);
                    triggerMemoryLifecycle(identity, request, response);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("[Orchestrator] FastPath[NONE] error! Duration: {}ms | Error: {}",
                            duration, error.getMessage(), error);
                })
                .onErrorResume(error -> handleFastPathError(error));
    }

    private Mono<String> processFastPathSearch(RequestContext ctx, String resolvedSystemPrompt,
                                                long startTime, UserRole userRole,
                                                WorkingContext workingCtx) {
        String request = ctx.getUserMessage();
        MemoryIdentity identity = ctx.getIdentity();
        String modelConfigId = ctx.getModelConfigId();
        String sessionId = identity.sessionId();

        log.info("[Orchestrator] FastPath[SEARCH]: routing to SearchAgent for '{}'", request);

        java.util.concurrent.atomic.AtomicLong llmDuration = new java.util.concurrent.atomic.AtomicLong();

        return Mono.zip(
                memoryService.buildWorkingContext(identity, userRole),
                chatHistoryService.getHistorySummaryWithBudget(sessionId, 20, maxHistoryChars, maxPerMessageChars)
        ).flatMap(tuple -> {
            String memoryContext = tuple.getT1();
            String historyContext = tuple.getT2();

            log.info("[DIAG-Perf] FastPath[SEARCH] memory+history loading: {}ms | memCtxLen={} | histCtxLen={}",
                    System.currentTimeMillis() - startTime,
                    memoryContext != null ? memoryContext.length() : 0,
                    historyContext != null ? historyContext.length() : 0);

            return Mono.justOrEmpty(agentRegistry.getAgent("search-agent"))
                    .switchIfEmpty(resolveBestAgentKeyword(request)
                            .doOnNext(agent -> log.info(
                                    "[Orchestrator] SearchAgent not in registry, fallback keyword routing to: {} | session={}",
                                    agent.getName(), sessionId)))
                    .flatMap(searchAgent -> {
                        log.info("[Orchestrator] Routing SEARCH to agent: {} | session={}",
                                searchAgent.getName(), sessionId);

                        LLMRequest llmRequest = LLMRequest.builder()
                                .sessionId(sessionId)
                                .userId(identity.userId())
                                .groupId(identity.groupId())
                                .systemPrompt(resolvedSystemPrompt)
                                .userMessage(request)
                                .modelConfigId(modelConfigId)
                                .build();

                        long t0 = System.currentTimeMillis();
                        return searchAgent.execute(llmRequest)
                                .doOnSuccess(r -> llmDuration.set(System.currentTimeMillis() - t0));
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        log.warn("[Orchestrator] No SearchAgent found for SEARCH, falling back to CONVERSATION: session={}",
                                sessionId);
                        return processFastPathConversation(ctx, resolvedSystemPrompt, startTime, userRole, workingCtx);
                    }));
        })
        .map(responseSanitizer::sanitize)
        .flatMap(response ->
                chatHistoryService.saveUserAndAssistantMessage(identity, request, response)
                        .thenReturn(response)
        )
        .doOnSuccess(response -> {
            long duration = System.currentTimeMillis() - startTime;
            log.info("[Orchestrator] FastPath[SEARCH] success! total={}ms | llm={}ms | Session: {}",
                    duration, llmDuration.get(), sessionId);
            triggerMemoryLifecycle(identity, request, response);
        })
        .onErrorResume(error -> handleFastPathError(error));
    }

    private Mono<String> processFastPathConversation(RequestContext ctx, String resolvedSystemPrompt,
                                                      long startTime, UserRole userRole,
                                                      WorkingContext workingCtx) {
        String request = ctx.getUserMessage();
        MemoryIdentity identity = ctx.getIdentity();
        String modelConfigId = ctx.getModelConfigId();
        String sessionId = identity.sessionId();

        log.info("[Orchestrator] FastPath[CONVERSATION]: with history for '{}'", request);

        long memStart = System.currentTimeMillis();
        return Mono.zip(
                memoryService.buildWorkingContext(identity, userRole),
                chatHistoryService.getHistorySummaryWithBudget(sessionId, 20, maxHistoryChars, maxPerMessageChars)
        ).flatMap(tuple -> {
            String memoryContext = tuple.getT1();
            String historyContext = tuple.getT2();
            long memElapsed = System.currentTimeMillis() - memStart;

            log.info("[DIAG-Perf] FastPath[CONVERSATION] memory+history loading: {}ms | memCtxLen={} | histCtxLen={}",
                    memElapsed,
                    memoryContext != null ? memoryContext.length() : 0,
                    historyContext != null ? historyContext.length() : 0);

            log.info("[PromptTokens] HistoryContext size={} chars (budget={} chars, perMsg={} chars)",
                    historyContext != null ? historyContext.length() : 0, maxHistoryChars, maxPerMessageChars);

            BuildContext buildCtx = BuildContext.builder()
                    .baseSystemPrompt(resolvedSystemPrompt)
                    .personaPrompt(personaMemoryStore.getPersonaMemoryText())
                    .userMessage(request)
                    .userProfile(ctx.getUserProfile())
                    .groupContext(ctx.getGroupContext())
                    .state(ctx.getSessionState())
                    .workingContext(workingCtx)
                    .extension("historyContext", historyContext)
                    .build();

            PromptAssemblyResult assembly = agentRuntime.assemble(buildCtx, PromptPolicy.CHAT_LIGHT);
            String fullPrompt = assembly.toFullPrompt(memoryContext,
                    "", "", PromptEnricher.EnrichmentResult.empty());
            String userPrompt = buildUserPrompt(request, historyContext);

            if (modelConfigId != null && !modelConfigId.isEmpty()) {
                return agentRuntime.runWithConfig(modelConfigId, fullPrompt, userPrompt);
            }
            return agentRuntime.run(fullPrompt, userPrompt);
        })
        .map(responseSanitizer::sanitize)
        .flatMap(response ->
                chatHistoryService.saveUserAndAssistantMessage(identity, request, response)
                        .thenReturn(response)
        )
        .doOnSuccess(response -> {
            long duration = System.currentTimeMillis() - startTime;
            log.info("[Orchestrator] FastPath[CONVERSATION] success! Duration: {}ms | Session: {}",
                    duration, sessionId);
            triggerMemoryLifecycle(identity, request, response);
        })
        .onErrorResume(error -> handleFastPathError(error));
    }

    private Mono<String> processFastPathDocument(RequestContext ctx, String resolvedSystemPrompt,
                                                  long startTime, UserRole userRole,
                                                  WorkingContext workingCtx) {
        String request = ctx.getUserMessage();
        MemoryIdentity identity = ctx.getIdentity();
        String modelConfigId = ctx.getModelConfigId();
        String sessionId = identity.sessionId();

        log.info("[Orchestrator] FastPath[DOCUMENT]: with artifact recall for '{}'", request);

        java.util.concurrent.atomic.AtomicLong llmDuration = new java.util.concurrent.atomic.AtomicLong();

        long memStart = System.currentTimeMillis();
        return Mono.zip(
                memoryService.buildWorkingContext(identity, userRole),
                chatHistoryService.getHistorySummaryWithBudget(sessionId, 20, maxHistoryChars, maxPerMessageChars)
        ).flatMap(tuple -> {
            String memoryContext = tuple.getT1();
            String historyContext = tuple.getT2();
            long memElapsed = System.currentTimeMillis() - memStart;

            log.info("[DIAG-Perf] FastPath[DOCUMENT] memory+history loading: {}ms | memCtxLen={} | histCtxLen={}",
                    memElapsed,
                    memoryContext != null ? memoryContext.length() : 0,
                    historyContext != null ? historyContext.length() : 0);

            log.info("[PromptTokens] HistoryContext size={} chars (budget={} chars, perMsg={} chars)",
                    historyContext != null ? historyContext.length() : 0, maxHistoryChars, maxPerMessageChars);

            long t0 = System.currentTimeMillis();

            String artifactContext = "";
            if (workingCtx.hasActiveArtifact()) {
                artifactContext = artifactService.recallRelevantContent(
                        workingCtx.getActiveArtifactId(),
                        request,
                        workingCtx.getActiveDocumentSummary()
                );
            } else if (workingCtx.hasActiveDocument()) {
                artifactContext = artifactService.recallByPath(
                        sessionId,
                        workingCtx.getActiveDocumentPath(),
                        request,
                        workingCtx.getActiveDocumentSummary()
                );
            }
            long t1 = System.currentTimeMillis();

            if (!artifactContext.isEmpty()) {
                log.info("[Orchestrator] FastPath[DOCUMENT]: artifact recalled, chars={}",
                        artifactContext.length());
                cacheArtifactSummary(workingCtx, artifactContext);
            }

            BuildContext buildCtx = BuildContext.builder()
                    .baseSystemPrompt(resolvedSystemPrompt)
                    .personaPrompt(personaMemoryStore.getPersonaMemoryText())
                    .userMessage(request)
                    .userProfile(ctx.getUserProfile())
                    .groupContext(ctx.getGroupContext())
                    .state(ctx.getSessionState())
                    .workingContext(workingCtx)
                    .extension("historyContext", historyContext)
                    .extension("artifactContext", artifactContext)
                    .build();

            boolean isGame = ctx.getSessionState() != null && ctx.getSessionState().isGameMode();
            PromptPolicy docPolicy = isGame ? PromptPolicy.GAME : PromptPolicy.CHAT;
            PromptAssemblyResult assembly = agentRuntime.assemble(buildCtx, docPolicy);
            long t2 = System.currentTimeMillis();

            String fullPrompt = assembly.toFullPrompt(memoryContext,
                    artifactContext,
                    isGame ? historyContext : "",
                    PromptEnricher.EnrichmentResult.empty());
            String userPrompt = isGame
                    ? buildUserPrompt(request, "")
                    : buildUserPrompt(request, historyContext);

            int systemChars = assembly.assembledPrompt() != null ? assembly.assembledPrompt().length() : 0;
            int memoryChars = memoryContext != null ? memoryContext.length() : 0;
            int artifactChars = artifactContext != null ? artifactContext.length() : 0;
            int historyChars = historyContext != null ? historyContext.length() : 0;
            int totalSystemChars = fullPrompt != null ? fullPrompt.length() : 0;
            log.info("[PromptTokens] System={}c | Memory={}c | Artifact={}c | History={}c | UserMsg={}c | TotalSystem={}c (~{} tok)",
                    systemChars, memoryChars, artifactChars, historyChars,
                    userPrompt != null ? userPrompt.length() : 0,
                    totalSystemChars, totalSystemChars / 2);

            log.info("[PipelineProfile] artifact_load={}ms | prompt_assemble={}ms",
                    t1 - t0, t2 - t1);

            long t3 = System.currentTimeMillis();
            Mono<String> llmCall;
            if (modelConfigId != null && !modelConfigId.isEmpty()) {
                llmCall = agentRuntime.runWithConfig(modelConfigId, fullPrompt, userPrompt);
            } else {
                llmCall = agentRuntime.run(fullPrompt, userPrompt);
            }
            return llmCall.doOnSuccess(r -> llmDuration.set(System.currentTimeMillis() - t3));
        })
        .map(responseSanitizer::sanitize)
        .flatMap(response ->
                chatHistoryService.saveUserAndAssistantMessage(identity, request, response)
                        .thenReturn(response)
        )
        .doOnSuccess(response -> {
            long duration = System.currentTimeMillis() - startTime;
            log.info("[Orchestrator] FastPath[DOCUMENT] success! total={}ms | llm={}ms | Session: {}",
                    duration, llmDuration.get(), sessionId);
            triggerMemoryLifecycle(identity, request, response);
        })
        .onErrorResume(error -> handleFastPathError(error));
    }

    private Mono<String> processFastPathWorkspace(RequestContext ctx, String resolvedSystemPrompt,
                                                   long startTime, UserRole userRole,
                                                   WorkingContext workingCtx) {
        String request = ctx.getUserMessage();
        MemoryIdentity identity = ctx.getIdentity();
        String modelConfigId = ctx.getModelConfigId();
        String sessionId = identity.sessionId();

        Workspace workspace = ctx.getWorkspace() != null
                ? ctx.getWorkspace()
                : workspaceService.loadByWorkspaceId(sessionId);

        String firstFilePath = extractFirstFilePath(request);

        log.info("[Orchestrator] FastPath[WORKSPACE]: with workspace for '{}'", request);

        long memStart = System.currentTimeMillis();
        return Mono.zip(
                memoryService.buildWorkingContext(identity, userRole),
                preloadFiles(request, workspace, sessionId),
                chatHistoryService.getHistorySummaryWithBudget(sessionId, 20, maxHistoryChars, maxPerMessageChars),
                Mono.just(workspace)
        ).flatMap(tuple -> {
            String memoryContext = tuple.getT1();
            String fileContext = tuple.getT2();
            String historyContext = tuple.getT3();
            Workspace ws = tuple.getT4();
            long memElapsed = System.currentTimeMillis() - memStart;

            log.info("[DIAG-Perf] FastPath[WORKSPACE] memory+file+history loading: {}ms | memCtxLen={} | fileCtxLen={} | histCtxLen={}",
                    memElapsed,
                    memoryContext != null ? memoryContext.length() : 0,
                    fileContext != null ? fileContext.length() : 0,
                    historyContext != null ? historyContext.length() : 0);
            String workspaceContext = ws != null && !ws.isEmpty()
                    ? ws.buildWorkspacePrompt()
                    : "";

            if (firstFilePath != null && !fileContext.isEmpty()) {
                java.util.Optional<com.mcp.common.artifact.Artifact> artifactOpt = artifactService.findByPath(sessionId, firstFilePath);
                workingCtx.setActiveDocument(
                        firstFilePath,
                        artifactOpt.map(com.mcp.common.artifact.Artifact::getId).orElse(null)
                );
                log.info("[Orchestrator] WorkingContext updated: activeDocumentPath={}, activeArtifactId={}, source=ARTIFACT",
                        firstFilePath, workingCtx.getActiveArtifactId());
            }

            String artifactContext = "";
            if (workingCtx.hasActiveArtifact()) {
                artifactContext = artifactService.recallRelevantContent(
                        workingCtx.getActiveArtifactId(),
                        request,
                        workingCtx.getActiveDocumentSummary()
                );
            }
            if (!artifactContext.isEmpty()) {
                cacheArtifactSummary(workingCtx, artifactContext);
            }

            String hostContext = !artifactContext.isEmpty() ? artifactContext : fileContext;
            BuildContext buildCtx = BuildContext.builder()
                    .baseSystemPrompt(resolvedSystemPrompt)
                    .personaPrompt(personaMemoryStore.getPersonaMemoryText())
                    .userMessage(request)
                    .workspacePrompt(workspaceContext)
                    .hostContextPrompt(hostContext)
                    .userProfile(ctx.getUserProfile())
                    .groupContext(ctx.getGroupContext())
                    .state(ctx.getSessionState())
                    .workingContext(workingCtx)
                    .extension("artifactContext", artifactContext)
                    .build();

            PromptAssemblyResult assembly = agentRuntime.assemble(buildCtx, PromptPolicy.CHAT);
            String fullPrompt = assembly.toFullPrompt(memoryContext,
                    artifactContext, historyContext, PromptEnricher.EnrichmentResult.empty());
            String userPrompt = buildUserPrompt(request, historyContext);

            int systemChars = assembly.assembledPrompt() != null ? assembly.assembledPrompt().length() : 0;
            int memoryChars = memoryContext != null ? memoryContext.length() : 0;
            int artifactChars = artifactContext != null ? artifactContext.length() : 0;
            int historyChars = historyContext != null ? historyContext.length() : 0;
            int totalSystemChars = fullPrompt != null ? fullPrompt.length() : 0;
            log.info("[PromptTokens] System={}c | Memory={}c | Artifact={}c | History={}c | UserMsg={}c | TotalSystem={}c (~{} tok)",
                    systemChars, memoryChars, artifactChars, historyChars,
                    userPrompt != null ? userPrompt.length() : 0,
                    totalSystemChars, totalSystemChars / 2);

            if (modelConfigId != null && !modelConfigId.isEmpty()) {
                return agentRuntime.runWithConfig(modelConfigId, fullPrompt, userPrompt);
            }
            return agentRuntime.run(fullPrompt, userPrompt);
        })
        .map(responseSanitizer::sanitize)
        .flatMap(response ->
                chatHistoryService.saveUserAndAssistantMessage(identity, request, response)
                        .thenReturn(response)
        )
        .doOnSuccess(response -> {
            long duration = System.currentTimeMillis() - startTime;
            log.info("[Orchestrator] FastPath[WORKSPACE] success! Duration: {}ms | Session: {}",
                    duration, sessionId);
            triggerMemoryLifecycle(identity, request, response);
        })
        .onErrorResume(error -> handleFastPathError(error));
    }

    private Mono<String> handleFastPathError(Throwable error) {
        String msg = error.getMessage() != null ? error.getMessage() : "Unknown error";
        if (msg.contains("429")) {
            return Mono.just("当前 API 配额已用尽，请稍后再试。");
        }
        return Mono.just("处理请求时发生错误: " + msg);
    }

    }