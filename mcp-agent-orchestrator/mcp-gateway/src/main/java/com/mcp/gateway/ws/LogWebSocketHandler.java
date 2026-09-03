package com.mcp.gateway.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.gateway.service.LogFileReaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class LogWebSocketHandler implements WebSocketHandler {

    private static final String DEFAULT_LOG_DIR = "log";
    private static final Map<String, String> MODULE_FILE_MAP = Map.of(
            "mcp-agent-orchestrator", "mcp-agent-orchestrator.log",
            "orchestrator", "orchestrator.log",
            "agent", "agent.log",
            "llm", "llm.log",
            "memory", "memory.log",
            "prompt", "prompt.log",
            "performance", "performance.log"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Map<String, String>> sessionFilters = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        Map<String, String> queryParams = parseQueryParams(session.getHandshakeInfo().getUri().getQuery());
        sessionFilters.put(sessionId, queryParams);

        log.info("[LogWS] Client connected: {} (module={}, level={})",
                sessionId, queryParams.get("module"), queryParams.get("level"));

        Flux<String> logStream = Flux.interval(Duration.ofSeconds(2))
                .flatMap(tick -> {
                    try {
                        List<Map<String, Object>> newEntries = readNewLogEntries(queryParams);
                        if (newEntries.isEmpty()) {
                            return Mono.empty();
                        }
                        String json = objectMapper.writeValueAsString(
                                Map.of("type", "logBatch", "entries", newEntries));
                        return Mono.just(json);
                    } catch (Exception e) {
                        return Mono.<String>error(e);
                    }
                })
                .onErrorContinue((err, obj) ->
                        log.error("[LogWS] Stream error: {}", err.getMessage()));

        return session.send(logStream.map(session::textMessage))
                .doFinally(signal -> {
                    sessionFilters.remove(sessionId);
                    log.info("[LogWS] Client disconnected: {}", sessionId);
                });
    }

    private List<Map<String, Object>> readNewLogEntries(Map<String, String> params) {
        String module = params.getOrDefault("module", "mcp-agent-orchestrator");
        String level = params.get("level");
        String fileName = MODULE_FILE_MAP.getOrDefault(module, module + ".log");
        Path logFile = Paths.get(DEFAULT_LOG_DIR).resolve(fileName);

        if (!Files.exists(logFile)) {
            return Collections.emptyList();
        }

        try {
            List<String> allLines = Files.readAllLines(logFile);
            int startLine = Math.max(0, allLines.size() - 20);
            List<Map<String, Object>> entries = new ArrayList<>();

            java.util.regex.Pattern logPattern = java.util.regex.Pattern.compile(
                    "^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\S+)\\s+\\[(\\S+)\\]\\s+(\\S+)\\s+-\\s+(.*)$");

            for (int i = startLine; i < allLines.size(); i++) {
                String line = allLines.get(i);
                java.util.regex.Matcher matcher = logPattern.matcher(line);
                if (!matcher.matches()) continue;

                String entryLevel = matcher.group(2);
                if (level != null && !level.isEmpty() && !level.equalsIgnoreCase(entryLevel)) {
                    continue;
                }

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("timestamp", matcher.group(1));
                entry.put("level", entryLevel);
                entry.put("thread", matcher.group(3));
                entry.put("logger", matcher.group(4));
                entry.put("message", matcher.group(5));
                entries.add(entry);
            }
            return entries;
        } catch (IOException e) {
            log.error("[LogWS] Failed to read log file: {}", logFile, e);
            return Collections.emptyList();
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(kv[0], kv[1]);
            }
        }
        return params;
    }
}