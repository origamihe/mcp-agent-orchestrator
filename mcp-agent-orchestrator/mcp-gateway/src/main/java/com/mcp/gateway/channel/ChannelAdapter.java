package com.mcp.gateway.channel;

import com.mcp.common.channel.ChannelMessage;
import com.mcp.common.channel.ChannelReply;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface ChannelAdapter {

    /** 渠道唯一标识，如 "qq", "telegram", "discord" */
    String getChannelType();

    /** 是否启用 */
    boolean isEnabled();

    /** 启动适配器（建立 WebSocket 连接等） */
    void start();

    /** 停止适配器 */
    void stop();

    /** 获取适配器运行状态 */
    Map<String, Object> getStatus();

    /** 将平台原始消息转换为通用 ChannelMessage */
    ChannelMessage normalize(Object rawPayload);

    /** 发送回复到平台 */
    Mono<Void> sendReply(ChannelReply reply);

    /** 健康检查 / 连接测试 */
    Mono<Boolean> healthCheck();

    /** 获取渠道配置的系统 Prompt（可选，默认返回 null） */
    default String getSystemPrompt() {
        return null;
    }

    /** 获取 Bot 在该平台的用户 ID（如 QQ 号），用于过滤群聊上下文中 Agent 自己的消息 */
    default String getBotUserId() {
        return null;
    }
}