package com.mcp.gateway.tts;

import com.mcp.common.tts.TtsService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class TtsServiceImpl implements TtsService {

    @Value("${tts.api-url:http://localhost:5000}")
    private String ttsApiUrl;

    @Value("${tts.output-dir:./voice-output}")
    private String outputDir;

    @Value("${tts.enabled:false}")
    private boolean ttsEnabled;

    private static final Duration TTS_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(3);
    private static final int CIRCUIT_BREAKER_THRESHOLD = 3;
    private static final Duration CIRCUIT_BREAKER_COOLDOWN = Duration.ofSeconds(30);

    private final AtomicBoolean serviceAvailable = new AtomicBoolean(false);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<Instant> circuitOpenUntil = new AtomicReference<>(Instant.MIN);

    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
            .clientConnector(new ReactorClientHttpConnector(
                    HttpClient.create().responseTimeout(Duration.ofSeconds(120))
            ))
            .build();

    @PostConstruct
    public void initHealthCheck() {
        if (!ttsEnabled) {
            log.info("[TTS] TTS is disabled by configuration");
            serviceAvailable.set(false);
            return;
        }
        healthCheck()
            .doOnNext(available -> {
                serviceAvailable.set(available);
                if (available) {
                    log.info("[TTS] Startup health check: TTS service is available at {}", ttsApiUrl);
                } else {
                    log.info("[TTS] Startup health check: TTS service is NOT available at {} (optional service, voice features will be disabled)", ttsApiUrl);
                }
            })
            .subscribe();
    }

    public boolean isAvailable() {
        if (!ttsEnabled) return false;
        if (circuitOpenUntil.get().isAfter(Instant.now())) return false;
        return serviceAvailable.get();
    }

    @Override
    public Mono<String> synthesize(String text, String voiceId) {
        if (!isAvailable() || text == null || text.isBlank()) {
            return Mono.empty();
        }

        return webClient.post()
                .uri(ttsApiUrl + "/tts")
                .bodyValue(Map.of(
                        "text", text,
                        "speaker", voiceId != null ? voiceId : "default"
                ))
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(TTS_TIMEOUT)
                .flatMap(audioBytes -> Mono.fromCallable(() -> {
                    Path dir = Paths.get(outputDir);
                    Files.createDirectories(dir);
                    String fileName = "tts_" + System.currentTimeMillis() + ".wav";
                    Path filePath = dir.resolve(fileName);
                    Files.write(filePath, audioBytes);
                    log.info("[TTS] Voice saved to: {}", filePath);
                    return filePath.toAbsolutePath().toString();
                }))
                .doOnSuccess(r -> recordSuccess())
                .doOnError(e -> {
                    log.error("[TTS] Synthesis failed: {}", e.getMessage());
                    recordFailure();
                });
    }

    @Override
    public Mono<byte[]> synthesizeToBytes(String text, String voiceId) {
        if (!isAvailable() || text == null || text.isBlank()) {
            return Mono.empty();
        }

        return webClient.post()
                .uri(ttsApiUrl + "/tts")
                .bodyValue(Map.of(
                        "text", text,
                        "voice", voiceId != null ? voiceId : "default",
                        "format", "wav"
                ))
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(TTS_TIMEOUT)
                .doOnSuccess(r -> recordSuccess())
                .doOnError(e -> {
                    log.error("[TTS] Synthesis failed: {}", e.getMessage());
                    recordFailure();
                });
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return webClient.get()
                .uri(ttsApiUrl + "/health")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(HEALTH_CHECK_TIMEOUT)
                .map(r -> true)
                .onErrorResume(e -> {
                    log.debug("[TTS] Health check failed: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    private void recordSuccess() {
        consecutiveFailures.set(0);
        serviceAvailable.set(true);
    }

    private void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
            serviceAvailable.set(false);
            circuitOpenUntil.set(Instant.now().plus(CIRCUIT_BREAKER_COOLDOWN));
            log.warn("[TTS] Circuit breaker opened after {} consecutive failures, cooldown until {}",
                    failures, circuitOpenUntil.get());
        }
    }
}