package com.mcp.gateway.channel;

import com.mcp.common.channel.ChannelMessage;
import com.mcp.common.channel.ChannelReply;
import com.mcp.common.channel.IntentType;
import com.mcp.common.channel.RecallMode;
import com.mcp.common.channel.SessionState;
import com.mcp.common.identity.GroupContext;
import com.mcp.common.identity.UserProfile;
import com.mcp.gateway.ws.WebSocketSessionManager;
import com.mcp.tools.tool.DocxGeneratorTool;
import com.mcp.tools.tool.PptGeneratorTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.file.Path;

@Slf4j
@Component
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

    @Value("${docx.output.dir:./generated/docx}")
    private String docxOutputDir;

    @Value("${ppt.output.dir:./generated/ppt}")
    private String pptOutputDir;

    public ChannelOrchestrator(ChannelAdapterRegistry adapterRegistry,
                                DocxGeneratorTool docxGeneratorTool,
                                PptGeneratorTool pptGeneratorTool,
                                WebSocketSessionManager wsSessionManager,
                                IntentRouter intentRouter,
                                PromptComposer promptComposer,
                                AgentFacade agentFacade,
                                ResponsePipeline responsePipeline,
                                UserProfileService userProfileService) {
        this.adapterRegistry = adapterRegistry;
        this.docxGeneratorTool = docxGeneratorTool;
        this.pptGeneratorTool = pptGeneratorTool;
        this.wsSessionManager = wsSessionManager;
        this.intentRouter = intentRouter;
        this.promptComposer = promptComposer;
        this.agentFacade = agentFacade;
        this.responsePipeline = responsePipeline;
        this.userProfileService = userProfileService;
    }

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

        log.info("[Channel:{}] Processing message from {} (chat={}): {}",
                channelType, msg.getSenderId(), msg.getChatId(), userMessage);

        // Step 2: 意图路由
        IntentRouter.IntentResult intentResult = intentRouter.detect(sessionId, userMessage);
        SessionState state = intentResult.state();

        // Step 3: 根据意图分发
        return switch (intentResult.intent()) {
            case GENERATE_DOCX -> handleDocxGeneration(msg, adapter, intentResult.task(), state);
            case GENERATE_PPT   -> handlePptGeneration(msg, adapter, intentResult.task(), state);
            case AMBIGUOUS      -> handleAmbiguous(msg, adapter, state);
            case RECALL_HISTORY -> handleRecallHistory(msg, adapter, userMessage, sessionId, state, intentResult.recallMode());
            default             -> handleChat(msg, adapter, userMessage, sessionId, state);
        };
    }

    /**
     * 普通聊天链路 - 注入身份信息、群上下文、分层 Prompt
     */
    private Mono<Void> handleChat(ChannelMessage msg, ChannelAdapter adapter,
                                   String userMessage, String sessionId, SessionState state) {
        // 获取用户身份
        UserProfile userProfile = userProfileService.getUserProfile(msg.getSenderId());

        // 获取群上下文
        GroupContext groupContext = null;
        if (msg.getChatType() == ChannelMessage.ChatType.GROUP && msg.getChatId() != null) {
            groupContext = userProfileService.getGroupContext(msg.getChatId());
        }

        // 构建分层 System Prompt
        String systemPrompt = promptComposer.buildLayeredSystemPrompt(
                adapter.getSystemPrompt(),
                null,   // developerPrompt 从配置读取
                null,   // personaPrompt 从配置读取
                userProfile,
                groupContext,
                state);

        log.info("[Channel:{}] User: {} ({}) | Role: {} | Relation: {} | Session: {}",
                adapter.getChannelType(),
                userProfile.getDisplayName(),
                msg.getSenderId(),
                userProfile.getRole(),
                userProfile.getRelation(),
                sessionId);

        return agentFacade.call(userMessage, sessionId, systemPrompt)
                .flatMap(agentResponse -> responsePipeline.process(
                        adapter.getChannelType(), msg, agentResponse, state))
                .flatMap(adapter::sendReply)
                .doOnSuccess(v -> log.info("[Channel:{}] Reply sent", adapter.getChannelType()))
                .doOnError(e -> log.error("[Channel:{}] Error: {}", adapter.getChannelType(), e.getMessage(), e))
                .then();
    }

    /**
     * 聊天历史回顾链路 — 从数据库读取真实 chat_messages 注入 Prompt
     */
    private Mono<Void> handleRecallHistory(ChannelMessage msg, ChannelAdapter adapter,
                                            String userMessage, String sessionId, SessionState state,
                                            RecallMode recallMode) {
        String systemPrompt = promptComposer.buildSystemPrompt(adapter.getSystemPrompt(), state);

        log.info("[Channel:{}] RECALL_HISTORY ({} mode) | Session: {}", adapter.getChannelType(), recallMode, sessionId);

        return agentFacade.callWithHistory(userMessage, sessionId, systemPrompt, recallMode)
                .flatMap(agentResponse -> responsePipeline.process(
                        adapter.getChannelType(), msg, agentResponse, state))
                .flatMap(adapter::sendReply)
                .doOnSuccess(v -> log.info("[Channel:{}] RECALL_HISTORY reply sent", adapter.getChannelType()))
                .doOnError(e -> log.error("[Channel:{}] RECALL_HISTORY error: {}", adapter.getChannelType(), e.getMessage(), e))
                .then();
    }

    /**
     * 歧义处理：用户同时提到文档和PPT，追问澄清
     */
    private Mono<Void> handleAmbiguous(ChannelMessage msg, ChannelAdapter adapter, SessionState state) {
        String clarifyMsg = "你想生成文档还是PPT？两个都做也行，告诉我一声就好。";
        ChannelReply reply = ChannelReply.builder()
                .channelType(adapter.getChannelType())
                .targetId(responsePipeline.getReplyTargetId(msg))
                .content(clarifyMsg)
                .chatType(msg.getChatType())
                .sendAsVoice(false)
                .build();
        return adapter.sendReply(reply).then();
    }

    /**
     * 文档生成链路 — 明确的双消息模式：先发文件，再发文本说明
     */
    private Mono<Void> handleDocxGeneration(ChannelMessage msg, ChannelAdapter adapter,
                                             GenerationTask task, SessionState state) {
        String docPrompt = promptComposer.buildDocxPrompt(task);
        String systemPrompt = promptComposer.buildSystemPrompt(adapter.getSystemPrompt(), state);
        String channelType = adapter.getChannelType();

        return agentFacade.call(docPrompt, msg.getPlatformSessionId(), systemPrompt)
                .flatMap(llmResponse -> {
                    try {
                        DocxGeneratorTool.DocxResult result = docxGeneratorTool.generateDocx(llmResponse, task.topic());
                        log.info("[Channel:{}] DOCX generated: {}", channelType, result.downloadUrl());
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

                        // 双消息模式：先发文件，再发文本说明
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
                .doOnSuccess(v -> log.info("[Channel:{}] DOCX reply sent", channelType))
                .doOnError(e -> log.error("[Channel:{}] DOCX error: {}", channelType, e.getMessage(), e))
                .then();
    }

    /**
     * PPT生成链路 — 明确的双消息模式：先发文件，再发文本说明
     */
    private Mono<Void> handlePptGeneration(ChannelMessage msg, ChannelAdapter adapter,
                                            GenerationTask task, SessionState state) {
        String pptPrompt = promptComposer.buildPptPrompt(task);
        String systemPrompt = promptComposer.buildSystemPrompt(adapter.getSystemPrompt(), state);
        String channelType = adapter.getChannelType();

        return agentFacade.call(pptPrompt, msg.getPlatformSessionId(), systemPrompt)
                .flatMap(llmResponse -> {
                    try {
                        PptGeneratorTool.PptResult result = pptGeneratorTool.generatePptx(llmResponse, task.topic());
                        log.info("[Channel:{}] PPT generated: {}", channelType, result.downloadUrl());
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

                        // 双消息模式：先发文件，再发文本说明
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
                .doOnSuccess(v -> log.info("[Channel:{}] PPT reply sent", channelType))
                .doOnError(e -> log.error("[Channel:{}] PPT error: {}", channelType, e.getMessage(), e))
                .then();
    }
}