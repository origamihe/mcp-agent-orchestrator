package com.mcp.tools.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.tools.annotation.McpTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class MultiSearchTool {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private static final int MAX_RESULTS_PER_SOURCE = 3;

    @Value("${websearch.serpapi.key:}")
    private String serpApiKey;

    @Value("${websearch.bing.key:}")
    private String bingApiKey;

    public MultiSearchTool(WebClient webSearchWebClient) {
        this.objectMapper = new ObjectMapper();
        this.webClient = webSearchWebClient;
    }

    @McpTool(
            name = "multi_search",
            description = "同时在多个搜索引擎中并行搜索同一个关键词，返回各来源的搜索结果并排对比。" +
                    "参数 query：搜索关键词。返回标注了来源（Google/DuckDuckGo/Wikipedia/Bing）的结构化结果，便于横向对比。",
            tags = {"search", "multi-source", "compare"}
    )
    public String multiSearch(String query) {
        if (query == null || query.isBlank()) {
            return "错误：未提供搜索关键词。";
        }

        log.info("Starting MULTI-SOURCE search for: {}", query);

        List<Mono<SearchSourceResult>> searches = new ArrayList<>();

        if (serpApiKey != null && !serpApiKey.isBlank()) {
            searches.add(searchGoogle(query).onErrorReturn(
                    new SearchSourceResult("Google (SerpAPI)", "Google 搜索暂时不可用: " + query, List.of())
            ));
        }

        searches.add(searchDuckDuckGo(query).onErrorReturn(
                new SearchSourceResult("DuckDuckGo", "DuckDuckGo 搜索暂时不可用", List.of())
        ));

        searches.add(searchWikipedia(query).onErrorReturn(
                new SearchSourceResult("Wikipedia", "Wikipedia 搜索暂时不可用", List.of())
        ));

        if (bingApiKey != null && !bingApiKey.isBlank()) {
            searches.add(searchBing(query).onErrorReturn(
                    new SearchSourceResult("Bing", "Bing 搜索暂时不可用", List.of())
            ));
        }

        List<SearchSourceResult> allResults = Flux.merge(searches)
                .collectList()
                .block(Duration.ofSeconds(20));

        if (allResults == null || allResults.isEmpty()) {
            return "多源搜索失败：所有搜索渠道均不可用。";
        }

        return formatMultiSourceResults(query, allResults);
    }

    private Mono<SearchSourceResult> searchGoogle(String query) {
        return Mono.fromCallable(() -> {
            String url = "https://serpapi.com/search?q=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8) +
                    "&api_key=" + serpApiKey +
                    "&engine=google&num=" + MAX_RESULTS_PER_SOURCE;

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .retryWhen(Retry.max(1))
                    .block(REQUEST_TIMEOUT);

            if (response == null || response.isBlank()) return null;

            JsonNode json = objectMapper.readTree(response);
            List<SearchResultItem> items = new ArrayList<>();
            JsonNode organic = json.path("organic_results");
            if (organic.isArray()) {
                for (JsonNode item : organic) {
                    if (items.size() >= MAX_RESULTS_PER_SOURCE) break;
                    String title = item.path("title").asText();
                    String snippet = item.path("snippet").asText();
                    String link = item.path("link").asText();
                    if (!title.isBlank()) {
                        items.add(new SearchResultItem(title, snippet, link));
                    }
                }
            }
            return new SearchSourceResult("Google", null, items);
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    private Mono<SearchSourceResult> searchDuckDuckGo(String query) {
        return Mono.fromCallable(() -> {
            String url = "https://api.duckduckgo.com/?q=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8) +
                    "&format=json&no_html=1&skip_disambig=1";

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .retryWhen(Retry.max(1))
                    .onErrorReturn("")
                    .block(REQUEST_TIMEOUT);

            if (response == null || response.isBlank()) return null;

            JsonNode json = objectMapper.readTree(response);
            List<SearchResultItem> items = new ArrayList<>();

            String absText = json.path("Abstract").asText();
            if (!absText.isBlank()) {
                items.add(new SearchResultItem("摘要", absText,
                        json.path("AbstractURL").asText()));
            }

            JsonNode related = json.path("RelatedTopics");
            if (related.isArray()) {
                for (JsonNode topic : related) {
                    if (items.size() >= MAX_RESULTS_PER_SOURCE + 1) break;
                    String text = topic.path("Text").asText();
                    String link = topic.path("FirstURL").asText();
                    if (!text.isBlank()) {
                        items.add(new SearchResultItem("", text, link));
                    }
                }
            }
            return new SearchSourceResult("DuckDuckGo", null, items);
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    private Mono<SearchSourceResult> searchWikipedia(String query) {
        return Mono.fromCallable(() -> {
            String url = "https://zh.wikipedia.org/w/api.php?action=query&list=search&srsearch=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8) +
                    "&format=json&srlimit=" + MAX_RESULTS_PER_SOURCE;

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .onErrorReturn("")
                    .block(REQUEST_TIMEOUT);

            if (response == null || response.isBlank()) return null;

            JsonNode json = objectMapper.readTree(response);
            JsonNode sr = json.path("query").path("search");
            List<SearchResultItem> items = new ArrayList<>();
            if (sr.isArray()) {
                for (JsonNode item : sr) {
                    if (items.size() >= MAX_RESULTS_PER_SOURCE) break;
                    String title = item.path("title").asText();
                    String snippet = item.path("snippet").asText().replaceAll("<[^>]+>", "");
                    if (!title.isBlank()) {
                        items.add(new SearchResultItem(title, snippet,
                                "https://zh.wikipedia.org/wiki/" + URLEncoder.encode(title, StandardCharsets.UTF_8)));
                    }
                }
            }
            return new SearchSourceResult("Wikipedia (中文)", null, items);
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    private Mono<SearchSourceResult> searchBing(String query) {
        return Mono.fromCallable(() -> {
            String url = "https://api.bing.microsoft.com/v7.0/search?q=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8) +
                    "&count=" + MAX_RESULTS_PER_SOURCE + "&mkt=zh-CN";

            String response = webClient.get()
                    .uri(url)
                    .header("Ocp-Apim-Subscription-Key", bingApiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .retryWhen(Retry.max(1))
                    .block(REQUEST_TIMEOUT);

            if (response == null || response.isBlank()) return null;

            JsonNode json = objectMapper.readTree(response);
            List<SearchResultItem> items = new ArrayList<>();
            JsonNode webPages = json.path("webPages").path("value");
            if (webPages.isArray()) {
                for (JsonNode page : webPages) {
                    if (items.size() >= MAX_RESULTS_PER_SOURCE) break;
                    items.add(new SearchResultItem(
                            page.path("name").asText(),
                            page.path("snippet").asText(),
                            page.path("url").asText()
                    ));
                }
            }
            return new SearchSourceResult("Bing", null, items);
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    private String formatMultiSourceResults(String query, List<SearchSourceResult> allResults) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("  多源联合搜索结果\n");
        sb.append("  查询关键词: ").append(query).append("\n");
        sb.append("  搜索来源数: ").append(allResults.size()).append("\n");
        sb.append("═══════════════════════════════════════\n\n");

        int sourceIdx = 1;
        for (SearchSourceResult source : allResults) {
            sb.append("【来源").append(sourceIdx).append("】").append(source.sourceName).append("\n");
            sb.append("───────────────────────────────────────\n");
            if (source.error != null) {
                sb.append("  ⚠ ").append(source.error).append("\n");
            } else if (source.items.isEmpty()) {
                sb.append("  (该来源未返回相关结果)\n");
            } else {
                int itemNum = 1;
                for (SearchResultItem item : source.items) {
                    sb.append("  ").append(itemNum).append(". ");
                    if (!item.title.isBlank()) sb.append(item.title).append("\n");
                    if (!item.snippet.isBlank()) sb.append("     ").append(item.snippet).append("\n");
                    if (!item.link.isBlank()) sb.append("     🔗 ").append(item.link).append("\n");
                    itemNum++;
                }
            }
            sb.append("\n");
            sourceIdx++;
        }

        sb.append("═══════════════════════════════════════\n");
        sb.append("提示：以上结果来自多个独立信息源，可能存在重复或矛盾。\n");
        sb.append("请使用 fetch_webpage 工具抓取感兴趣链接的详细内容，进行横向对比。\n");
        return sb.toString();
    }

    private record SearchSourceResult(String sourceName, String error, List<SearchResultItem> items) {
        SearchSourceResult(String sourceName, String error, List<SearchResultItem> items) {
            this.sourceName = sourceName;
            this.error = error;
            this.items = items != null ? items : List.of();
        }
    }

    private record SearchResultItem(String title, String snippet, String link) {}
}