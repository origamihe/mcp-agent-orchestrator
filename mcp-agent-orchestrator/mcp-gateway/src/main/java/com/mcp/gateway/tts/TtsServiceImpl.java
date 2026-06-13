package com.mcp.gateway.tts;

import com.mcp.common.tts.TtsService;
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
import java.util.Map;

@Slf4j
@Service
public class TtsServiceImpl implements TtsService {

    @Value("${tts.api-url:http://localhost:5000}")
    private String ttsApiUrl;

    @Value("${tts.output-dir:./voice-output}")
    private String outputDir;

    @Value("${tts.enabled:false}")
    private boolean ttsEnabled;

    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
            .clientConnector(new ReactorClientHttpConnector(
                    HttpClient.create().responseTimeout(Duration.ofSeconds(120))
            ))
            .build();

    @Override
    public Mono<String> synthesize(String text, String voiceId) {
        if (!ttsEnabled || text == null || text.isBlank()) {
            return Mono.empty();
        }

        return webClient.post()
                .uri(ttsApiUrl + "/tts")
                .bodyValue(Map.of(
                        "text", text,
                        "voice", voiceId != null ? voiceId : "default"
                ))
                .retrieve()
                .bodyToMono(byte[].class)
                .flatMap(audioBytes -> Mono.fromCallable(() -> {
                    Path dir = Paths.get(outputDir);
                    Files.createDirectories(dir);
                    String fileName = "tts_" + System.currentTimeMillis() + ".wav";
                    Path filePath = dir.resolve(fileName);
                    Files.write(filePath, audioBytes);
                    log.info("[TTS] Voice saved to: {}", filePath);
                    return filePath.toAbsolutePath().toString();
                }))
                .doOnError(e -> log.error("[TTS] Synthesis failed: {}", e.getMessage()));
    }

    @Override
    public Mono<byte[]> synthesizeToBytes(String text, String voiceId) {
        if (!ttsEnabled || text == null || text.isBlank()) {
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
                .timeout(Duration.ofSeconds(120))
                .doOnError(e -> log.error("[TTS] Synthesis failed: {}", e.getMessage()));
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return webClient.get()
                .uri(ttsApiUrl + "/health")
                .retrieve()
                .bodyToMono(String.class)
                .map(r -> true)
                .onErrorReturn(false);
    }
}