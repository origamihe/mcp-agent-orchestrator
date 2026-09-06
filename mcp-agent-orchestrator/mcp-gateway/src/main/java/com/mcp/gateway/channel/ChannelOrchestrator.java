package com.mcp.gateway.channel;

import com.mcp.common.channel.ActiveContextSource;
import com.mcp.common.channel.ChannelMessage;
import com.mcp.common.channel.ChannelReply;
import com.mcp.common.channel.IntentType;
import com.mcp.common.channel.RecallMode;
import com.mcp.common.channel.SessionState;
import com.mcp.common.channel.WorkingContext;
import com.mcp.common.channel.WorldState;
import com.mcp.common.context.RequestContext;
import com.mcp.common.identity.MemoryIdentity;
import com.mcp.common.identity.GroupContext;
import com.mcp.common.identity.UserProfile;
import com.mcp.common.identity.UserProfileService;
import com.mcp.common.workspace.Workspace;
import com.mcp.common.delivery.DeliveryMessage;
import com.mcp.engine.delivery.DeliveryManager;
import com.mcp.engine.reflection.ReflectionJudge;
import com.mcp.engine.workspace.WorkspaceService;
import com.mcp.engine.world.WorldStateService;
import com.mcp.engine.memory.GroupMemoryService;
import com.mcp.engine.task.AgentTask;
import com.mcp.engine.task.AgentTaskScheduler;
import com.mcp.gateway.decision.InteractionDecisionEngine;
import com.mcp.gateway.handler.ChannelErrorHandler;
import com.mcp.gateway.ws.WebSocketSessionManager;
import com.mcp.tools.tool.DocxGeneratorTool;
import com.mcp.tools.tool.PptGeneratorTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelOrchestrator {

    private final ChannelAdapterRegistry adapterRegistry;
    private final DocxGeneratorTool docxGeneratorTool;
    private final PptGeneratorTool pptGeneratorTool;
    private final WebSocketSessionManager wsSessionManager;
    private final IntentRouter intentRouter;
    private final PromptComposer promptComposer;
    private final AgentFacade agentFacade;
    private final ResponsePipeline responsePipeline;
    private final UserProfileService userProfileService;
    private final ReflectionJudge reflectionJudge;
    private final WorldStateService worldStateService;
    private final WorkspaceService workspaceService;
    private final GroupMemoryService groupMemoryService;
    private final InteractionDecisionEngine decisionEngine;
    private final AgentTaskScheduler taskScheduler;
    private final ChannelErrorHandler channelErrorHandler;
    private final DeliveryManager deliveryManager;
    private final ConcurrentHashMap<String, WorkingContext> workingContexts = new ConcurrentHashMap<>();

    @Value("${docx.output.dir:./generated/docx}")
    private String docxOutputDir;

    @Value("${ppt.output.dir:./generated/ppt}")
    private String pptOutputDir;

    /**
     * 统一的渠道消息处理入口
     * 所有渠道的消息都走这里，与平台无关
     */
    public Mono<Void> handleMessage(String channelType, Object rawPayload) {
        ChannelAdapter adapter = adapterRegistry.get(channelType)
                .orElseThrow(() -> new IllegalArgumentException("Unknown channel: " + channelType));

        if (!adapter.isEnabled()) {
            return Mono.empty();
        }

        // Step 1: 平台消息 → 通用模型
        ChannelMessage msg = adapter.normalize(rawPayload);
        if (msg.getContent() == null || msg.getContent().trim().isEmpty()) {
            return Mono.empty();
        }

        String userMessage = msg.getContent().trim();
        String sessionId = msg.getPlatformSessionId();

        log.debug("[Channel:{}] Processing message from {} (chat={}): {}",
                channelType, msg.getSenderId(), msg.getChatId(), userMessage);

        // Step 2: 所有群聊消息异步记录到 GroupMemory
        if (msg.getChatType() == ChannelMessage.ChatType.GROUP) {
            groupMemoryService.recordMessage(msg);
        }

        // Step 3: 交互决策引擎 — 决定处理方式
        InteractionDecisionEngine.Decision decision = decisionEngine.decide(msg);

        return switch (decision.type()) {
            case IGNORE -> {
                log.debug("[Channel:{}] Decision=IGNORE: {} group={} user={}",
                        channelType, decision.reason(), msg.getChatId(), msg.getSenderId());
                yield Mono.empty();
            }

            case MERGE -> {
                log.debug("[Channel:{}] Decision=MERGE: {} group={} user={} thread={}",
                        channelType, decision.reason(), msg.getChatId(), msg.getSenderId(), decision.threadId());
                yield Mono.empty();
            }

            case QUEUE -> {
                AgentTask task = AgentTask.create(sessionId, msg.getChatId(),
                        msg.getSenderId(), msg.getSenderName(),
                        userProfileService.getUserProfile(msg.getSenderId()).getRole(),
                        userMessage, decision.threadId(),
                        decision.priority(), decision.reason());
                AgentTaskScheduler.ScheduleResult result = taskScheduler.submit(task);
                log.debug("[Channel:{}] Decision=QUEUE: {} group={} user={} taskId={} result={}",
                        channelType, decision.reason(), msg.getChatId(), msg.getSenderId(),
                        task.getTaskId(), result);
                yield Mono.empty();
            }

            case INTERRUPT -> {
                AgentTask task = AgentTask.create(sessionId, msg.getChatId(),
                        msg.getSenderId(), msg.getSenderName(),
                        userProfileService.getUserProfile(msg.getSenderId()).getRole(),
                        userMessage, decision.threadId(),
                        decision.priority(), decision.reason());
                AgentTaskScheduler.ScheduleResult result = taskScheduler.submit(task);
                log.debug("[Channel:{}] Decision=INTERRUPT: {} group={} user={} taskId={} result={}",
                        channelType, decision.reason(), msg.getChatId(), msg.getSenderId(),
                        task.getTaskId(), result);
                yield dispatchToAgent(msg, adapter, userMessage, sessionId, task);
            }

            case REPLY -> {
                AgentTask task = AgentTask.create(sessionId, msg.getChatId(),
                        msg.getSenderId(), msg.getSenderName(),
                        userProfileService.getUserProfile(msg.getSenderId()).getRole(),
                        userMessage, decision.threadId(),
                        decision.priority(), decision.reason());
                taskScheduler.submit(task);
                log.debug("[Channel:{}] Decision=REPLY: {} group={} user={} thread={}",
                        channelType, decision.reason(), msg.getChatId(), msg.getSenderId(), decision.threadId());
                yield dispatchToAgent(msg, adapter, userMessage, sessionId, task);
            }
        };
    }

    /**
     * 分发到 Agent 处理（意图路由 + 执行）。
     */
    private Mono<Void> dispatchToAgent(ChannelMessage msg, ChannelAdapter adapter,
                                        String userMessage, String sessionId, AgentTask agentTask) {
        IntentRouter.IntentResult intentResult = intentRouter.detect(sessionId, userMessage);
        SessionState state = intentResult.state();

        return switch (intentResult.intent()) {
            case GENERATE_DOCX -> handleDocxGeneration(msg, adapter, intentResult.task(), state, agentTask);
            case GENERATE_PPT   -> handlePptGeneration(msg, adapter, intentResult.task(), state, agentTask);
            case AMBIGUOUS      -> handleAmbiguous(msg, adapter, state, agentTask);
            case RECALL_HISTORY -> handleRecallHistory(msg, adapter, userMessage, sessionId, state, intentResult.recallMode(), agentTask);
            case SEARCH          -> handleSearch(msg, adapter, userMessage, sessionId, state, agentTask);
            default             -> handleChat(msg, adapter, userMessage, sessionId, state, agentTask);
        };
    }

    /**
     * 普通聊天链路 - 注入身份信息、群上下文、分层 Prompt、Workspace 和 Host 上下文
     */
    private Mono<Void> handleChat(ChannelMessage msg, ChannelAdapter adapter,
                                   String userMessage, String sessionId, SessionState state,
                                   AgentTask agentTask) {
        // 加载持久化的世界状态
        loadWorldStateIfNeeded(sessionId, state);

        // 加载持久化的工作空间
        Workspace workspace = loadWorkspaceIfNeeded(sessionId, msg);

        // 获取用户身份
        UserProfile userProfile = userProfileService.getUserProfile(msg.getSenderId());

        // 获取群上下文
        GroupContext groupContext = null;
        if (msg.getChatType() == ChannelMessage.ChatType.GROUP && msg.getChatId() != null) {
            groupContext = userProfileService.getGroupContext(msg.getChatId());
        }

        // 原始 System Prompt — 分层组装由 AgentRuntime 统一完成
        String systemPrompt = adapter.getSystemPrompt();

        // 构建 MemoryIdentity（源头已有 senderId/groupId/platform，无需后续解析 sessionId）
        MemoryIdentity identity = new MemoryIdentity(
                adapter.getChannelType(),
                sessionId,
                msg.getSenderId(),
                msg.getChatType() == ChannelMessage.ChatType.GROUP ? msg.getChatId() : null,
                null
        );

        log.debug("[Channel:{}] User: {} ({}) | Role: {} | Relation: {} | Session: {} | Workspace: {}",
                adapter.getChannelType(),
                userProfile.getDisplayName(),
                msg.getSenderId(),
                userProfile.getRole(),
                userProfile.getRelation(),
                sessionId,
                workspace != null ? workspace.getWorkspaceId() : "none");

        RequestContext ctx = RequestContext.builder()
                .identity(identity)
                .userProfile(userProfile)
                .groupContext(groupContext)
                .sessionState(state)
                .workingContext(workingContexts.computeIfAbsent(sessionId, k -> new WorkingContext()))
                .workspace(workspace)
                .userMessage(userMessage)
                .systemPrompt(systemPrompt)
                .threadId(agentTask != null ? agentTask.getThreadId() : null)
                .botUserId(adapter.getBotUserId())
                .build();

        return agentFacade.call(ctx)
                .timeout(channelErrorHandler.getDefaultTimeout())
                .flatMap(agentResponse -> validateAndRetry(
                        agentResponse, userMessage, sessionId, systemPrompt, state, 0))
                .flatMap(agentResponse -> {
                    trackDelivery(adapter.getChannelType(), msg.getSenderId(), agentResponse);
                    return responsePipeline.process(
                            adapter.getChannelType(), msg, agentResponse, state);
                })
                .flatMap(adapter::sendReply)
                .doOnSuccess(v -> {
                    log.info("[Channel:{}] Reply sent", adapter.getChannelType());
                    saveWorldStateIfNeeded(sessionId, state);
                    saveWorkspaceIfNeeded(sessionId, workspace, msg);
                    onTaskCompleted(agentTask, adapter);
                })
                .doOnError(e -> {
                    log.error("[Channel:{}] Error: session={} | errorType={} | message={}", adapter.getChannelType(), msg.getPlatformSessionId(), e.getClass().getSimpleName(), e.getMessage(), e);
                    onTaskFailed(agentTask, adapter, e.getMessage());
                })
                .onErrorResume(e -> {
                    ChannelReply fallback = channelErrorHandler.buildFallbackReply(msg, e);
                    return adapter.sendReply(fallback).then();
                })
                .then();
    }

    /**
     * 角色锁校验 + 反射重试循环。
     * 仅在角色模式（GAME/NPC）下生效，CHAT/CODING/WORKFLOW 等模式直接透传。
     * 最多重试 2 次，超过则降级发送（记录警告日志）。
     */
    private Mono<String> validateAndRetry(String response, String userMessage, String sessionId,
                                           String systemPrompt, SessionState state, int retryCount) {
        if (!state.getMode().isRoleMode() || state.getRoleRuntime() == null) {
            return Mono.just(response);
        }

        return reflectionJudge.judge(response, state.getRoleRuntime())
                .flatMap(result -> {
                    if (result.passed()) {
                        if (retryCount > 0) {
                            log.info("[ReflectionJudge] Retry #{} passed, returning corrected response", retryCount);
                        }
                        return Mono.just(response);
                    }

                    int nextRetry = retryCount + 1;
                    if (nextRetry > reflectionJudge.getMaxRetry()) {
                        log.warn("[ReflectionJudge] Max retries ({}) exceeded, returning original response with violation",
                                reflectionJudge.getMaxRetry());
                        return Mono.just(response);
                    }

                    log.warn("[ReflectionJudge] Violation detected, retrying (#{}/{})...",
                            nextRetry, reflectionJudge.getMaxRetry());

                    String reflectionWarning = reflectionJudge.buildReflectionPrompt(response);
                    String retrySystemPrompt = systemPrompt + "\n\n" + reflectionWarning;

                    return agentFacade.call(userMessage, sessionId, retrySystemPrompt)
                            .flatMap(retryResponse -> validateAndRetry(
                                    retryResponse, userMessage, sessionId, systemPrompt, state, nextRetry));
                });
    }

    /**
     * 角色模式时从 DB 加载持久化的世界状态。
     */
    private void loadWorldStateIfNeeded(String sessionId, SessionState state) {
        if (!state.getMode().isRoleMode()) return;
        if (state.getWorldState() != null && !state.getWorldState().isEmpty()) return;

        try {
            WorldState loaded = worldStateService.loadBySessionId(sessionId);
            if (!loaded.isEmpty()) {
                state.setWorldState(loaded);
                log.debug("[WorldState] Loaded persisted world state for session {}", sessionId);
            }
        } catch (Exception e) {
            log.warn("[WorldState] Failed to load world state for session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 角色模式时将世界状态保存到 DB。
     */
    private void saveWorldStateIfNeeded(String sessionId, SessionState state) {
        if (!state.getMode().isRoleMode()) return;
        if (state.getWorldState() == null || state.getWorldState().isEmpty()) return;

        try {
            worldStateService.save(sessionId, state.getWorldState());
        } catch (Exception e) {
            log.warn("[WorldState] Failed to save world state for session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 加载持久化的工作空间。
     * workspaceId 由 sessionId 派生，确保每个会话拥有独立的工作空间。
     * 如果 Host 提供了 HostContext（如 IDE 的当前文件），则合并到 Workspace 中。
     */
    private Workspace loadWorkspaceIfNeeded(String sessionId, ChannelMessage msg) {
        String workspaceId = deriveWorkspaceId(sessionId, msg);
        try {
            Workspace workspace = workspaceService.loadByWorkspaceId(workspaceId);
            if (workspace.isEmpty()) {
                workspace.setWorkspaceId(workspaceId);
                workspace.setName("workspace-" + workspaceId);
                workspace.markDirty();
                log.debug("[Workspace] Created new workspace {}", workspaceId);
            } else {
                log.debug("[Workspace] Loaded workspace {} (last active: {})",
                        workspaceId, workspace.getLastActiveAt());
            }

            // 从 HostContext 中提取工作空间信息并合并
            mergeHostContextToWorkspace(workspace, msg);

            return workspace;
        } catch (Exception e) {
            log.warn("[Workspace] Failed to load workspace {}: {}", workspaceId, e.getMessage());
            Workspace ws = new Workspace();
            ws.setWorkspaceId(workspaceId);
            return ws;
        }
    }

    /**
     * 保存工作空间到 DB。
     */
    private void saveWorkspaceIfNeeded(String sessionId, Workspace workspace, ChannelMessage msg) {
        if (workspace == null) return;
        try {
            workspace.setLastActiveAt(java.time.Instant.now());
            workspace.markDirty();
            workspaceService.save(workspace);
            log.debug("[Workspace] Saved workspace {}", workspace.getWorkspaceId());
        } catch (Exception e) {
            log.warn("[Workspace] Failed to save workspace {}: {}",
                    workspace.getWorkspaceId(), e.getMessage());
        }
    }

    /**
     * 从 HostContext 合并工作空间信息。
     * 例如 IDE Host 传入的当前文件路径、Git 状态等。
     */
    private void mergeHostContextToWorkspace(Workspace workspace, ChannelMessage msg) {
        if (msg.getHostContext() == null) return;

        var hc = msg.getHostContext();
        boolean modified = false;

        if (hc.getCurrentFilePath() != null) {
            workspace.setLastActiveFile(hc.getCurrentFilePath());
            workspace.setLastActiveLine(hc.getCursorLine());
            modified = true;
        }
        if (hc.getProjectPath() != null) {
            workspace.setProjectPath(hc.getProjectPath());
            modified = true;
        }
        if (hc.getProjectFiles() != null && !hc.getProjectFiles().isEmpty()) {
            workspace.setFileTreeSnapshot(hc.getProjectFiles());
            modified = true;
        }
        if (hc.getGitBranch() != null) {
            var git = workspace.getGitState();
            if (git == null) {
                git = new Workspace.GitState();
                workspace.setGitState(git);
            }
            git.setBranch(hc.getGitBranch());
            if (hc.getGitStatus() != null) git.setStatus(hc.getGitStatus());
            if (hc.getGitDiff() != null) git.setDiff(hc.getGitDiff());
            modified = true;
        }
        if (hc.getTerminalCwd() != null) {
            var term = workspace.getTerminalState();
            if (term == null) {
                term = new Workspace.TerminalState();
                workspace.setTerminalState(term);
            }
            term.setCwd(hc.getTerminalCwd());
            if (hc.getTerminalOutput() != null) term.setLastOutput(hc.getTerminalOutput());
            modified = true;
        }

        if (modified) {
            workspace.markDirty();
        }
    }

    /**
     * 从 sessionId 和 Host 信息派生 workspaceId。
     * 对于 IDE Host，使用项目路径作为 workspaceId（更稳定）。
     * 对于其他 Host，使用 sessionId 作为 workspaceId。
     */
    private String deriveWorkspaceId(String sessionId, ChannelMessage msg) {
        if (msg.getHostContext() != null && msg.getHostContext().getProjectPath() != null) {
            String projectPath = msg.getHostContext().getProjectPath();
            String sanitized = projectPath.replaceAll("[^a-zA-Z0-9_\\-]", "-");
            return "project-" + sanitized.replaceAll("-{2,}", "-");
        }
        return "workspace-" + sessionId;
    }

    /**
     * 聊天历史回顾链路 — 从数据库读取真实 chat_messages 注入 Prompt
     */
    private Mono<Void> handleRecallHistory(ChannelMessage msg, ChannelAdapter adapter,
                                            String userMessage, String sessionId, SessionState state,
                                            RecallMode recallMode, AgentTask agentTask) {
        String systemPrompt = adapter.getSystemPrompt();

        log.debug("[Channel:{}] RECALL_HISTORY ({} mode) | Session: {}", adapter.getChannelType(), recallMode, sessionId);

        return agentFacade.callWithHistory(userMessage, sessionId, systemPrompt, recallMode)
                .timeout(channelErrorHandler.getDefaultTimeout())
                .flatMap(agentResponse -> responsePipeline.process(
                        adapter.getChannelType(), msg, agentResponse, state))
                .flatMap(adapter::sendReply)
                .doOnSuccess(v -> {
                    log.debug("[Channel:{}] RECALL_HISTORY reply sent", adapter.getChannelType());
                    onTaskCompleted(agentTask, adapter);
                })
                .doOnError(e -> {
                    log.error("[Channel:{}] RECALL_HISTORY error: {}", adapter.getChannelType(), e.getMessage(), e);
                    onTaskFailed(agentTask, adapter, e.getMessage());
                })
                .onErrorResume(e -> {
                    ChannelReply fallback = channelErrorHandler.buildFallbackReply(msg, e);
                    return adapter.sendReply(fallback).then();
                })
                .then();
    }

    /**
     * 歧义处理：用户同时提到文档和PPT，追问澄清
     */
    private Mono<Void> handleAmbiguous(ChannelMessage msg, ChannelAdapter adapter,
                                        SessionState state, AgentTask agentTask) {
        String clarifyMsg = "你想生成文档还是PPT？两个都做也行，告诉我一声就好。";
        ChannelReply reply = ChannelReply.builder()
                .channelType(adapter.getChannelType())
                .targetId(responsePipeline.getReplyTargetId(msg))
                .content(clarifyMsg)
                .chatType(msg.getChatType())
                .sendAsVoice(false)
                .build();
        return adapter.sendReply(reply)
                .doOnSuccess(v -> onTaskCompleted(agentTask, adapter))
                .doOnError(e -> onTaskFailed(agentTask, adapter, e.getMessage()))
                .onErrorResume(e -> {
                    ChannelReply fallback = channelErrorHandler.buildFallbackReply(msg, e);
                    return adapter.sendReply(fallback).then();
                })
                .then();
    }

    /**
     * 搜索链路 — 使用 SearchAgent 进行联网搜索，调用搜索工具获取最新信息。
     */
    private Mono<Void> handleSearch(ChannelMessage msg, ChannelAdapter adapter,
                                     String userMessage, String sessionId, SessionState state,
                                     AgentTask agentTask) {
        String systemPrompt = adapter.getSystemPrompt();

        UserProfile userProfile = userProfileService.getUserProfile(msg.getSenderId());

        GroupContext groupContext = null;
        if (msg.getChatType() == ChannelMessage.ChatType.GROUP && msg.getChatId() != null) {
            groupContext = userProfileService.getGroupContext(msg.getChatId());
        }

        Workspace workspace = loadWorkspaceIfNeeded(sessionId, msg);

        MemoryIdentity identity = new MemoryIdentity(
                adapter.getChannelType(),
                sessionId,
                msg.getSenderId(),
                msg.getChatType() == ChannelMessage.ChatType.GROUP ? msg.getChatId() : null,
                null
        );

        WorkingContext workingCtx = workingContexts.computeIfAbsent(sessionId, k -> new WorkingContext());
        workingCtx.setActiveContextSource(ActiveContextSource.SEARCH_RESULT);
        workingCtx.setCurrentTask("SEARCH: " + userMessage);

        log.debug("[Channel:{}] SEARCH: query='{}' | User: {} ({}) | Session: {}",
                adapter.getChannelType(), userMessage,
                userProfile.getDisplayName(), msg.getSenderId(), sessionId);

        RequestContext ctx = RequestContext.builder()
                .identity(identity)
                .userProfile(userProfile)
                .groupContext(groupContext)
                .sessionState(state)
                .workingContext(workingCtx)
                .workspace(workspace)
                .userMessage(userMessage)
                .systemPrompt(systemPrompt)
                .threadId(agentTask != null ? agentTask.getThreadId() : null)
                .botUserId(adapter.getBotUserId())
                .build();

        return agentFacade.call(ctx)
                .timeout(channelErrorHandler.getDefaultTimeout())
                .flatMap(agentResponse -> responsePipeline.process(
                        adapter.getChannelType(), msg, agentResponse, state))
                .flatMap(adapter::sendReply)
                .doOnSuccess(v -> {
                    log.info("[Channel:{}] SEARCH reply sent", adapter.getChannelType());
                    saveWorkspaceIfNeeded(sessionId, workspace, msg);
                    onTaskCompleted(agentTask, adapter);
                })
                .doOnError(e -> {
                    log.error("[Channel:{}] SEARCH error: {}", adapter.getChannelType(), e.getMessage(), e);
                    onTaskFailed(agentTask, adapter, e.getMessage());
                })
                .onErrorResume(e -> {
                    ChannelReply fallback = channelErrorHandler.buildFallbackReply(msg, e);
                    return adapter.sendReply(fallback).then();
                })
                .then();
    }

    /**
     * 文档生成链路 — 明确的双消息模式：先发文件，再发文本说明
     */
    private Mono<Void> handleDocxGeneration(ChannelMessage msg, ChannelAdapter adapter,
                                             GenerationTask task, SessionState state,
                                             AgentTask agentTask) {
        String topic = task.topic();
        String sessionId = msg.getPlatformSessionId();
        String systemPrompt = """
                你是一个专业的搜索与研究助手。请根据用户提供的主题进行深度搜索和信息整理，
                最终以Markdown格式输出一份结构清晰、内容详实的文档。
                
                【严格要求】
                1. 必须先调用搜索工具获取最新信息，严禁直接凭已有知识回答
                2. 搜索完成后，以Markdown格式整理成文档，包含以下结构：
                   ## 标题
                   ## 核心发现
                   ## 详细分析
                   ## 信息来源
                3. 确保内容专业、准确、有深度
                4. 使用中文
                """;
        String channelType = adapter.getChannelType();

        MemoryIdentity identity = new MemoryIdentity(
                adapter.getChannelType(),
                sessionId,
                msg.getSenderId(),
                msg.getChatType() == ChannelMessage.ChatType.GROUP ? msg.getChatId() : null,
                null
        );

        UserProfile userProfile = userProfileService.getUserProfile(msg.getSenderId());

        GroupContext groupContext = null;
        if (msg.getChatType() == ChannelMessage.ChatType.GROUP && msg.getChatId() != null) {
            groupContext = userProfileService.getGroupContext(msg.getChatId());
        }

        Workspace workspace = loadWorkspaceIfNeeded(sessionId, msg);

        WorkingContext workingCtx = workingContexts.computeIfAbsent(sessionId, k -> new WorkingContext());
        workingCtx.setActiveContextSource(ActiveContextSource.TASK);
        workingCtx.setCurrentTask("DOCX_GENERATION: " + topic);

        log.debug("[Channel:{}] DOCX generation: topic='{}' | User: {} ({}) | Session: {}",
                channelType, topic,
                userProfile.getDisplayName(), msg.getSenderId(), sessionId);

        RequestContext ctx = RequestContext.builder()
                .identity(identity)
                .userProfile(userProfile)
                .groupContext(groupContext)
                .sessionState(state)
                .workingContext(workingCtx)
                .workspace(workspace)
                .userMessage(topic)
                .systemPrompt(systemPrompt)
                .threadId(agentTask != null ? agentTask.getThreadId() : null)
                .botUserId(adapter.getBotUserId())
                .build();

        return agentFacade.call(ctx)
                .timeout(channelErrorHandler.getDefaultTimeout())
                .flatMap(markdownResponse -> {
                    try {
                        DocxGeneratorTool.DocxResult result = docxGeneratorTool.generateDocxFromMarkdown(
                                markdownResponse, topic);
                        log.debug("[Channel:{}] DOCX generated from markdown: {}", channelType, result.downloadUrl());
                        wsSessionManager.broadcast(result.downloadUrl());

                        Path filePath = Path.of(docxOutputDir, result.fileName()).toAbsolutePath().normalize();
                        ChannelReply fileReply = ChannelReply.builder()
                                .channelType(channelType)
                                .targetId(responsePipeline.getReplyTargetId(msg))
                                .chatType(msg.getChatType())
                                .sendAsFile(true)
                                .filePath(filePath.toString())
                                .fileUrl(result.downloadUrl())
                                .content(result.message())
                                .build();

                        return adapter.sendReply(fileReply)
                                .then(Mono.just(result.message()));
                    } catch (Exception e) {
                        log.error("[Channel:{}] DOCX generation failed: {}", channelType, e.getMessage(), e);
                        return Mono.just("文档生成失败，请稍后重试。");
                    }
                })
                .flatMap(textMsg -> {
                    ChannelReply textReply = ChannelReply.builder()
                            .channelType(channelType)
                            .targetId(responsePipeline.getReplyTargetId(msg))
                            .content(textMsg)
                            .chatType(msg.getChatType())
                            .sendAsVoice(false)
                            .build();
                    return adapter.sendReply(textReply);
                })
                .doOnSuccess(v -> {
                    log.info("[Channel:{}] DOCX reply sent", channelType);
                    onTaskCompleted(agentTask, adapter);
                })
                .doOnError(e -> {
                    log.error("[Channel:{}] DOCX error: {}", channelType, e.getMessage(), e);
                    onTaskFailed(agentTask, adapter, e.getMessage());
                })
                .onErrorResume(e -> {
                    ChannelReply fallback = channelErrorHandler.buildFallbackReply(msg, e);
                    return adapter.sendReply(fallback).then();
                })
                .then();
    }

    /**
     * PPT生成链路 — 明确的双消息模式：先发文件，再发文本说明
     */
    private Mono<Void> handlePptGeneration(ChannelMessage msg, ChannelAdapter adapter,
                                            GenerationTask task, SessionState state,
                                            AgentTask agentTask) {
        String topic = task.topic();
        String sessionId = msg.getPlatformSessionId();
        String pptPrompt = promptComposer.buildPptPrompt(task);
        String channelType = adapter.getChannelType();

        MemoryIdentity identity = new MemoryIdentity(
                adapter.getChannelType(),
                sessionId,
                msg.getSenderId(),
                msg.getChatType() == ChannelMessage.ChatType.GROUP ? msg.getChatId() : null,
                null
        );

        UserProfile userProfile = userProfileService.getUserProfile(msg.getSenderId());

        GroupContext groupContext = null;
        if (msg.getChatType() == ChannelMessage.ChatType.GROUP && msg.getChatId() != null) {
            groupContext = userProfileService.getGroupContext(msg.getChatId());
        }

        Workspace workspace = loadWorkspaceIfNeeded(sessionId, msg);

        WorkingContext workingCtx = workingContexts.computeIfAbsent(sessionId, k -> new WorkingContext());
        workingCtx.setActiveContextSource(ActiveContextSource.TASK);
        workingCtx.setCurrentTask("PPT_GENERATION: " + topic);

        log.debug("[Channel:{}] PPT generation: topic='{}' | User: {} ({}) | Session: {}",
                channelType, topic,
                userProfile.getDisplayName(), msg.getSenderId(), sessionId);

        RequestContext ctx = RequestContext.builder()
                .identity(identity)
                .userProfile(userProfile)
                .groupContext(groupContext)
                .sessionState(state)
                .workingContext(workingCtx)
                .workspace(workspace)
                .userMessage(pptPrompt)
                .systemPrompt(adapter.getSystemPrompt())
                .threadId(agentTask != null ? agentTask.getThreadId() : null)
                .botUserId(adapter.getBotUserId())
                .build();

        return agentFacade.call(ctx)
                .timeout(channelErrorHandler.getDefaultTimeout())
                .flatMap(llmResponse -> {
                    try {
                        PptGeneratorTool.PptResult result = pptGeneratorTool.generatePptx(llmResponse, topic);
                        log.debug("[Channel:{}] PPT generated: {}", channelType, result.downloadUrl());
                        wsSessionManager.broadcast(result.downloadUrl());

                        Path filePath = Path.of(pptOutputDir, result.fileName()).toAbsolutePath().normalize();
                        ChannelReply fileReply = ChannelReply.builder()
                                .channelType(channelType)
                                .targetId(responsePipeline.getReplyTargetId(msg))
                                .chatType(msg.getChatType())
                                .sendAsFile(true)
                                .filePath(filePath.toString())
                                .fileUrl(result.downloadUrl())
                                .content(result.message())
                                .build();

                        return adapter.sendReply(fileReply)
                                .then(Mono.just(result.message()));
                    } catch (Exception e) {
                        log.error("[Channel:{}] PPT generation failed: {}", channelType, e.getMessage(), e);
                        return Mono.just("PPT生成失败，请稍后重试。");
                    }
                })
                .flatMap(textMsg -> {
                    ChannelReply textReply = ChannelReply.builder()
                            .channelType(channelType)
                            .targetId(responsePipeline.getReplyTargetId(msg))
                            .content(textMsg)
                            .chatType(msg.getChatType())
                            .sendAsVoice(false)
                            .build();
                    return adapter.sendReply(textReply);
                })
                .doOnSuccess(v -> {
                    log.info("[Channel:{}] PPT reply sent", channelType);
                    onTaskCompleted(agentTask, adapter);
                })
                .doOnError(e -> {
                    log.error("[Channel:{}] PPT error: {}", channelType, e.getMessage(), e);
                    onTaskFailed(agentTask, adapter, e.getMessage());
                })
                .onErrorResume(e -> {
                    ChannelReply fallback = channelErrorHandler.buildFallbackReply(msg, e);
                    return adapter.sendReply(fallback).then();
                })
                .then();
    }

    /**
     * 任务完成钩子 — 标记任务完成，调度下一个排队任务。
     */
    private void onTaskCompleted(AgentTask agentTask, ChannelAdapter adapter) {
        if (agentTask == null || agentTask.getGroupId() == null) return;
        taskScheduler.completeTask(agentTask.getGroupId())
                .ifPresent(nextTask -> dispatchNextTask(nextTask, adapter));
    }

    /**
     * 任务失败钩子 — 标记任务失败，调度下一个排队任务。
     */
    private void onTaskFailed(AgentTask agentTask, ChannelAdapter adapter, String error) {
        if (agentTask == null || agentTask.getGroupId() == null) return;
        taskScheduler.failTask(agentTask.getGroupId(), error)
                .ifPresent(nextTask -> dispatchNextTask(nextTask, adapter));
    }

    /**
     * 调度下一个排队任务 — 从队列中取出任务并重新进入 Agent 处理链路。
     */
    private void dispatchNextTask(AgentTask nextTask, ChannelAdapter adapter) {
        log.debug("[Channel:{}] 调度下一个任务 {} group={} user={} priority={}",
                adapter.getChannelType(), nextTask.getTaskId(),
                nextTask.getGroupId(), nextTask.getUserId(), nextTask.getPriority());

        ChannelMessage syntheticMsg = ChannelMessage.builder()
                .messageId("dequeue-" + nextTask.getTaskId())
                .platformSessionId(nextTask.getSessionId())
                .senderId(nextTask.getUserId())
                .senderName(nextTask.getUserName())
                .chatId(nextTask.getGroupId())
                .chatType(ChannelMessage.ChatType.GROUP)
                .content(nextTask.getMessageContent())
                .mentionedAgent(true)
                .build();

        dispatchToAgent(syntheticMsg, adapter,
                nextTask.getMessageContent(), nextTask.getSessionId(), nextTask)
                .subscribe(
                        v -> log.info("[Channel:{}] 排队任务 {} 执行完成",
                                adapter.getChannelType(), nextTask.getTaskId()),
                        e -> log.error("[Channel:{}] 排队任务 {} 执行失败: {}",
                                adapter.getChannelType(), nextTask.getTaskId(), e.getMessage())
                );
    }

    /**
     * 记录消息投递到 DeliveryManager，用于追踪和重试。
     */
    private void trackDelivery(String channelType, String targetId, String content) {
        try {
            DeliveryMessage deliveryMsg = DeliveryMessage.text(channelType, targetId, content);
            deliveryManager.deliver(deliveryMsg);
        } catch (Exception e) {
            log.warn("[Channel:{}] Failed to track delivery: {}", channelType, e.getMessage());
        }
    }
}