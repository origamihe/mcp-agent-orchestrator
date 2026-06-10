package com.mcp.gateway.channel;

import com.mcp.common.channel.ChannelMessage;
import com.mcp.common.channel.ChannelReply;
import com.mcp.engine.orchestrator.AgentOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelOrchestrator {

    private final AgentOrchestrator agentOrchestrator;
    private final ChannelAdapterRegistry adapterRegistry;

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

        log.info("[Channel:{}] Processing message from {} (chat={}): {}",
                channelType, msg.getSenderId(), msg.getChatId(), msg.getContent());

        // Step 2: 调用 Agent 业务层（完全平台无关）
        return agentOrchestrator.processRequestWithSystemPrompt(
                        msg.getContent(),
                        msg.getPlatformSessionId(),
                        adapter.getSystemPrompt(),
                        null    // modelConfigId 可配置
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(agentResponse -> {
                    // Step 3: 通用回复 → 平台发送
                    ChannelReply reply = ChannelReply.builder()
                            .channelType(channelType)
                            .targetId(getReplyTargetId(msg))
                            .content(agentResponse)
                            .chatType(msg.getChatType())
                            .build();
                    return adapter.sendReply(reply);
                })
                .doOnSuccess(v -> log.info("[Channel:{}] Reply sent", channelType))
                .doOnError(e -> log.error("[Channel:{}] Error: {}", channelType, e.getMessage(), e))
                .then();
    }

    private String getReplyTargetId(ChannelMessage msg) {
        return msg.getChatType() == ChannelMessage.ChatType.GROUP
                ? msg.getChatId()
                : msg.getSenderId();
    }
}