package com.mcp.tools.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.tools.annotation.McpTool;
import com.mcp.tools.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSearchTool {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private static final Duration SINGLE_SOURCE_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration TOTAL_SEARCH_BUDGET = Duration.ofSeconds(5);
    private static final int MAX_RESULTS = 5;
    private static final long CACHE_TTL_SECONDS = 60;

    @Value("${websearch.serpapi.key:}")
    private String serpApiKey;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public WebSearchTool() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @McpTool(
            name = "web_search",
            description = "进行联网搜索，获取最新的信息、新闻、技术文档等。参数 query：搜索关键词",
            tags = {"search", "web", "internet"}
    )
    public ToolResult webSearch(String query) {
        if (query == null || query.isBlank()) {
            return ToolResult.failure("未提供搜索关键词", null, "web_search");
        }

        CacheEntry cached = cache.get(query);
        if (cached != null && !cached.isExpired()) {
            log.info("[WebSearch] Cache hit for: {}", query);
            return cached.result;
        }

        log.info("Starting web search for query: {}", query);

        List<Mono<SourceResult>> searchTasks = new ArrayList<>();

        if (serpApiKey != null && !serpApiKey.isBlank()) {
            searchTasks.add(searchWithSerpApiAsync(query));
        }
        searchTasks.add(searchWithDuckDuckGoAsync(query));
        searchTasks.add(searchWithWikipediaAsync(query));

        Map<String, SourceResult> results = new LinkedHashMap<>();
        try {
            List<SourceResult> collected = FluxMergeWithBudget(searchTasks);
            for (SourceResult sr : collected) {
                results.put(sr.sourceName, sr);
            }
        } catch (Exception e) {
            log.error("[WebSearch] Search budget exceeded: {}", e.getMessage());
        }

        ToolResult result = buildStructuredResult(query, results);
        cache.put(query, new CacheEntry(result, Instant.now().plusSeconds(CACHE_TTL_SECONDS)));
        return result;
    }

    private Mono<SourceResult> searchWithSerpApiAsync(String query) {
        return Mono.fromCallable(() -> {
            String url = "https://serpapi.com/search?q=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8) +
                    "&api_key=" + serpApiKey +
                    "&engine=google&num=" + MAX_RESULTS;

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(SINGLE_SOURCE_TIMEOUT)
                    .block(SINGLE_SOURCE_TIMEOUT);

            if (response == null || response.isBlank()) {
                return SourceResult.empty("SerpAPI");
            }

            JsonNode json = objectMapper.readTree(response);
            List<String> results = new ArrayList<>();
            JsonNode organic = json.path("organic_results");
            if (organic.isArray()) {
                int count = 0;
                for (JsonNode item : organic) {
                    if (count >= MAX_RESULTS) break;
                    String title = item.path("title").asText();
                    String snippet = item.path("snippet").asText();
                    String link = item.path("link").asText();
                    if (!title.isBlank()) {
                        count++;
                        results.add(String.format("%d. %s\n   %s\n   链接: %s", count, title, snippet, link));
                    }
                }
            }

            if (results.isEmpty()) {
                return SourceResult.empty("SerpAPI");
            }
            log.info("SerpAPI 搜索完成: {} ({} 条结果)", query, results.size());
            return SourceResult.success("SerpAPI", results);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> {
              log.warn("SerpAPI 搜索异常: {}", e.getMessage());
              return Mono.just(SourceResult.failure("SerpAPI", e.getMessage()));
          });
    }

    private Mono<SourceResult> searchWithDuckDuckGoAsync(String query) {
        return Mono.fromCallable(() -> {
            String url = "https://api.duckduckgo.com/?q=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8) +
                    "&format=json&no_html=1&skip_disambig=1";

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(SINGLE_SOURCE_TIMEOUT)
                    .onErrorReturn("")
                    .block(SINGLE_SOURCE_TIMEOUT);

            if (response == null || response.isBlank()) {
                return SourceResult.empty("DuckDuckGo");
            }

            JsonNode json = objectMapper.readTree(response);
            List<String> results = new ArrayList<>();

            String abstractText = json.path("Abstract").asText();
            if (!abstractText.isBlank()) {
                results.add("摘要: " + abstractText);
            }

            String answer = json.path("Answer").asText();
            if (!answer.isBlank()) {
                results.add("答案: " + answer);
            }

            JsonNode relatedTopics = json.path("RelatedTopics");
            if (relatedTopics.isArray()) {
                int count = 0;
                for (JsonNode topic : relatedTopics) {
                    if (count >= MAX_RESULTS) break;
                    String title = topic.path("Text").asText();
                    String link = topic.path("FirstURL").asText();
                    if (!title.isBlank()) {
                        count++;
                        results.add(String.format("%d. %s\n   链接: %s", count, title, link));
                    }
                }
            }

            if (results.isEmpty()) {
                return SourceResult.empty("DuckDuckGo");
            }
            log.info("DuckDuckGo 搜索完成: {} ({} 条结果)", query, results.size());
            return SourceResult.success("DuckDuckGo", results);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> {
              log.warn("DuckDuckGo 搜索异常: {}", e.getMessage());
              return Mono.just(SourceResult.failure("DuckDuckGo", e.getMessage()));
          });
    }

    private Mono<SourceResult> searchWithWikipediaAsync(String query) {
        return Mono.fromCallable(() -> {
            String url = "https://zh.wikipedia.org/w/api.php?action=query&list=search&srsearch=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8) +
                    "&format=json&srlimit=" + MAX_RESULTS;

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(SINGLE_SOURCE_TIMEOUT)
                    .onErrorReturn("")
                    .block(SINGLE_SOURCE_TIMEOUT);

            if (response == null || response.isBlank()) {
                return SourceResult.empty("Wikipedia");
            }

            JsonNode json = objectMapper.readTree(response);
            JsonNode searchResults = json.path("query").path("search");
            List<String> results = new ArrayList<>();

            if (searchResults.isArray()) {
                int count = 0;
                for (JsonNode item : searchResults) {
                    if (count >= MAX_RESULTS) break;
                    String title = item.path("title").asText();
                    String snippet = item.path("snippet").asText()
                            .replaceAll("<[^>]+>", "");
                    if (!title.isBlank()) {
                        count++;
                        results.add(String.format("%d. %s\n   %s", count, title, snippet));
                    }
                }
            }

            if (results.isEmpty()) {
                return SourceResult.empty("Wikipedia");
            }
            log.info("Wikipedia 搜索完成: {} ({} 条结果)", query, results.size());
            return SourceResult.success("Wikipedia", results);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(e -> {
              log.warn("Wikipedia 搜索异常: {}", e.getMessage());
              return Mono.just(SourceResult.failure("Wikipedia", e.getMessage()));
          });
    }

    private List<SourceResult> FluxMergeWithBudget(List<Mono<SourceResult>> tasks) {
        List<SourceResult> collected = Collections.synchronizedList(new ArrayList<>());
        Instant deadline = Instant.now().plus(TOTAL_SEARCH_BUDGET);

        List<Thread> threads = new ArrayList<>();
        for (Mono<SourceResult> task : tasks) {
            Thread t = new Thread(() -> {
                try {
                    SourceResult result = task.block(Duration.between(Instant.now(), deadline));
                    if (result != null) {
                        collected.add(result);
                    }
                } catch (Exception e) {
                    log.debug("[WebSearch] Source task cancelled (budget): {}", e.getMessage());
                }
            });
            threads.add(t);
            t.start();
        }

        for (Thread t : threads) {
            try {
                long remaining = Duration.between(Instant.now(), deadline).toMillis();
                if (remaining <= 0) break;
                t.join(remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return new ArrayList<>(collected);
    }

    private ToolResult buildStructuredResult(String query, Map<String, SourceResult> results) {
        List<SourceStatus> sources = new ArrayList<>();
        List<String> allContent = new ArrayList<>();

        for (SourceResult sr : results.values()) {
            sources.add(new SourceStatus(sr.sourceName, sr.ok, sr.error));
            if (sr.ok && sr.items != null) {
                allContent.add("【" + sr.sourceName + "】\n" + String.join("\n\n", sr.items));
            }
        }

        if (allContent.isEmpty()) {
            return ToolResult.failure(
                    "搜索失败：所有搜索渠道均不可用。建议检查网络连接或稍后重试。",
                    query, "web_search");
        }

        boolean allOk = sources.stream().allMatch(s -> s.ok);
        if (allOk) {
            return ToolResult.success("搜索完成，共 " + allContent.size() + " 条结果", query, "web_search")
                    .withData(buildResultData(query, sources, allContent));
        } else {
            return ToolResult.failure(
                    "部分搜索渠道不可用，仅返回可用结果", query, "web_search")
                    .withData(buildResultData(query, sources, allContent));
        }
    }

    private String buildResultData(String query, List<SourceStatus> sources, List<String> contentParts) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"query\":\"").append(escapeJson(query)).append("\"");
        sb.append(",\"sources\":[");
        for (int i = 0; i < sources.size(); i++) {
            if (i > 0) sb.append(",");
            SourceStatus s = sources.get(i);
            sb.append("{\"name\":\"").append(escapeJson(s.name)).append("\"");
            sb.append(",\"ok\":").append(s.ok);
            if (s.error != null) {
                sb.append(",\"error\":\"").append(escapeJson(s.error)).append("\"");
            }
            sb.append("}");
        }
        sb.append("]");
        if (!contentParts.isEmpty()) {
            sb.append(",\"content\":\"").append(escapeJson(String.join("\n\n", contentParts))).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private record SourceResult(String sourceName, boolean ok, List<String> items, String error) {
        static SourceResult success(String name, List<String> items) {
            return new SourceResult(name, true, items, null);
        }
        static SourceResult empty(String name) {
            return new SourceResult(name, false, List.of(), "无结果");
        }
        static SourceResult failure(String name, String error) {
            return new SourceResult(name, false, List.of(), error);
        }
    }

    private record SourceStatus(String name, boolean ok, String error) {}

    private enum SearchStatus { SUCCESS, PARTIAL_SUCCESS, FAILURE }

    private record CacheEntry(ToolResult result, Instant expiresAt) {
        boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    }
}