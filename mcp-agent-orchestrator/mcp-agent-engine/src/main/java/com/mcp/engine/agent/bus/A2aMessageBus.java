package com.mcp.engine.agent.bus;

import com.mcp.engine.agent.card.A2aProtocol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A2A 消息总线 - Agent 间通信基础设施
 * <p>
 * 提供 Agent 间的点对点任务委派和广播消息传递。
 * 基于 Reactor Sinks 实现响应式消息流。
 */
@Slf4j
@Component
public class A2aMessageBus {

    private final Sinks.Many<A2aProtocol.AgentBroadcast> broadcastSink =
            Sinks.many().multicast().onBackpressureBuffer();

    private final Map<String, Sinks.Many<A2aProtocol.AgentTaskRequest>> taskSinks =
            new ConcurrentHashMap<>();

    private final Map<String, Sinks.Many<A2aProtocol.AgentTaskResponse>> responseSinks =
            new ConcurrentHashMap<>();

    /**
     * 注册 Agent 的任务接收端口
     */
    public void registerAgent(String agentId) {
        taskSinks.putIfAbsent(agentId,
                Sinks.many().multicast().onBackpressureBuffer());
        responseSinks.putIfAbsent(agentId,
                Sinks.many().multicast().onBackpressureBuffer());
        log.info("[A2aBus] Agent registered on bus: {}", agentId);
    }

    /**
     * 注销 Agent
     */
    public void unregisterAgent(String agentId) {
        Sinks.Many<A2aProtocol.AgentTaskRequest> taskSink = taskSinks.remove(agentId);
        if (taskSink != null) {
            taskSink.tryEmitComplete();
        }
        Sinks.Many<A2aProtocol.AgentTaskResponse> respSink = responseSinks.remove(agentId);
        if (respSink != null) {
            respSink.tryEmitComplete();
        }
        log.info("[A2aBus] Agent unregistered from bus: {}", agentId);
    }

    /**
     * 发送任务请求到指定 Agent（点对点）
     */
    public Mono<A2aProtocol.AgentTaskResponse> sendTask(A2aProtocol.AgentTaskRequest request) {
        Sinks.Many<A2aProtocol.AgentTaskRequest> taskSink = taskSinks.get(request.getToAgentId());
        if (taskSink == null) {
            return Mono.just(A2aProtocol.AgentTaskResponse.builder()
                    .taskId(request.getTaskId())
                    .fromAgentId(request.getToAgentId())
                    .toAgentId(request.getFromAgentId())
                    .success(false)
                    .errorMessage("Agent not found: " + request.getToAgentId())
                    .build());
        }

        taskSink.tryEmitNext(request);
        log.info("[A2aBus] Task sent: {} -> {} (taskId={})",
                request.getFromAgentId(), request.getToAgentId(), request.getTaskId());

        return Mono.just(A2aProtocol.AgentTaskResponse.builder()
                .taskId(request.getTaskId())
                .fromAgentId(request.getToAgentId())
                .toAgentId(request.getFromAgentId())
                .success(true)
                .result("Task dispatched")
                .build());
    }

    /**
     * 发送响应
     */
    public void sendResponse(A2aProtocol.AgentTaskResponse response) {
        Sinks.Many<A2aProtocol.AgentTaskResponse> respSink = responseSinks.get(response.getToAgentId());
        if (respSink != null) {
            respSink.tryEmitNext(response);
            log.info("[A2aBus] Response sent: {} -> {} (taskId={})",
                    response.getFromAgentId(), response.getToAgentId(), response.getTaskId());
        }
    }

    /**
     * 广播消息到所有 Agent
     */
    public void broadcast(A2aProtocol.AgentBroadcast broadcast) {
        broadcastSink.tryEmitNext(broadcast);
        log.info("[A2aBus] Broadcast: topic={}, from={}", broadcast.getTopic(), broadcast.getFromAgentId());
    }

    /**
     * 监听指定 Agent 的任务请求流
     */
    public Flux<A2aProtocol.AgentTaskRequest> listenForTasks(String agentId) {
        Sinks.Many<A2aProtocol.AgentTaskRequest> sink = taskSinks.get(agentId);
        return sink != null ? sink.asFlux() : Flux.empty();
    }

    /**
     * 监听指定 Agent 的响应流
     */
    public Flux<A2aProtocol.AgentTaskResponse> listenForResponses(String agentId) {
        Sinks.Many<A2aProtocol.AgentTaskResponse> sink = responseSinks.get(agentId);
        return sink != null ? sink.asFlux() : Flux.empty();
    }

    /**
     * 广播流
     */
    public Flux<A2aProtocol.AgentBroadcast> broadcastStream() {
        return broadcastSink.asFlux();
    }
}