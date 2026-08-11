package com.mcp.engine.routing;

import com.mcp.core.domain.llm.LlmModelConfig;
import com.mcp.core.service.LlmConfigService;
import com.mcp.llm.client.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 模型 Fallback 链 — 当主模型调用失败或超时时，自动切换到备选模型。
 *
 * 使用方式：
 * 1. 构建 fallback 链：fallbackChain.buildChain(primaryConfigId, fallbackConfigIds...)
 * 2. 通过链执行：fallbackChain.executeWithFallback(chain, systemPrompt, userPrompt)
 * 3. 也支持流式执行：fallbackChain.executeStreamWithFallback(chain, systemPrompt, userPrompt)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelFallbackChain {

    private final LlmClient llmClient;
    private final LlmConfigService llmConfigService;

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    public record FallbackEntry(String configId, String description) {
        @Override
        public String toString() {
            return configId + " (" + description + ")";
        }
    }

    public List<FallbackEntry> buildChain(String primaryConfigId, String... fallbackConfigIds) {
        List<FallbackEntry> chain = new ArrayList<>();
        chain.add(new FallbackEntry(primaryConfigId, "primary"));
        for (String id : fallbackConfigIds) {
            chain.add(new FallbackEntry(id, "fallback"));
        }
        return chain;
    }

    public List<FallbackEntry> buildChainFromConfigs(LlmModelConfig primary, List<LlmModelConfig> fallbacks) {
        List<FallbackEntry> chain = new ArrayList<>();
        chain.add(new FallbackEntry(primary.getConfigId(), "primary"));
        for (LlmModelConfig fb : fallbacks) {
            chain.add(new FallbackEntry(fb.getConfigId(), "fallback"));
        }
        return chain;
    }

    public Mono<String> executeWithFallback(List<FallbackEntry> chain, String systemPrompt, String userPrompt) {
        return executeWithFallback(chain, systemPrompt, userPrompt, DEFAULT_TIMEOUT);
    }

    public Mono<String> executeWithFallback(List<FallbackEntry> chain, String systemPrompt, String userPrompt,
                                             Duration timeout) {
        if (chain == null || chain.isEmpty()) {
            return llmClient.generateWithSystemPrompt(systemPrompt, userPrompt);
        }

        return tryExecute(chain, 0, systemPrompt, userPrompt, timeout);
    }

    private Mono<String> tryExecute(List<FallbackEntry> chain, int index, String systemPrompt,
                                     String userPrompt, Duration timeout) {
        if (index >= chain.size()) {
            return Mono.error(new RuntimeException("All models in fallback chain exhausted"));
        }

        FallbackEntry entry = chain.get(index);

        return executeWithEntry(entry, systemPrompt, userPrompt)
                .timeout(timeout)
                .doOnSuccess(r -> log.info("[ModelFallbackChain] Success with {}: {}",
                        entry, r.length() > 100 ? r.substring(0, 100) + "..." : r))
                .onErrorResume(error -> {
                    log.warn("[ModelFallbackChain] Failed with {}: {} | Trying next in chain...",
                            entry, error.getMessage());
                    return tryExecute(chain, index + 1, systemPrompt, userPrompt, timeout);
                });
    }

    private Mono<String> executeWithEntry(FallbackEntry entry, String systemPrompt, String userPrompt) {
        return llmConfigService.getConfigById(entry.configId())
                .flatMap(config -> {
                    log.info("[ModelFallbackChain] Trying model: {}/{} (configId={})",
                            config.getProvider().getCode(), config.getModelName(), entry.configId());
                    return llmClient.generateWithConfigAndSystem(entry.configId(), systemPrompt, userPrompt);
                });
    }

    // ====================== 流式 Fallback ======================

    public Flux<String> executeStreamWithFallback(List<FallbackEntry> chain, String systemPrompt, String userPrompt) {
        return executeStreamWithFallback(chain, systemPrompt, userPrompt, DEFAULT_TIMEOUT);
    }

    public Flux<String> executeStreamWithFallback(List<FallbackEntry> chain, String systemPrompt, String userPrompt,
                                                   Duration timeout) {
        if (chain == null || chain.isEmpty()) {
            return llmClient.generateStreamWithSystemPrompt(systemPrompt, userPrompt);
        }

        return tryExecuteStream(chain, 0, systemPrompt, userPrompt, timeout);
    }

    private Flux<String> tryExecuteStream(List<FallbackEntry> chain, int index, String systemPrompt,
                                           String userPrompt, Duration timeout) {
        if (index >= chain.size()) {
            return Flux.error(new RuntimeException("All models in fallback chain exhausted"));
        }

        FallbackEntry entry = chain.get(index);

        return executeStreamWithEntry(entry, systemPrompt, userPrompt)
                .timeout(timeout, Flux.error(new RuntimeException("Stream timeout: " + entry)))
                .doOnComplete(() -> log.info("[ModelFallbackChain] Stream success with {}", entry))
                .onErrorResume(error -> {
                    log.warn("[ModelFallbackChain] Stream failed with {}: {} | Trying next in chain...",
                            entry, error.getMessage());
                    return tryExecuteStream(chain, index + 1, systemPrompt, userPrompt, timeout);
                });
    }

    private Flux<String> executeStreamWithEntry(FallbackEntry entry, String systemPrompt, String userPrompt) {
        log.info("[ModelFallbackChain] Streaming with model configId={}", entry.configId());
        return llmClient.generateStreamWithConfigAndSystem(entry.configId(), systemPrompt, userPrompt);
    }
}