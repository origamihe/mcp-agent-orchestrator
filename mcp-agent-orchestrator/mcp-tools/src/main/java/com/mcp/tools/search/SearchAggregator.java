package com.mcp.tools.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.tools.model.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SearchAggregator {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final SearchReranker reranker;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private static final int MAX_RESULTS_PER_SOURCE = 5;

    @Value("${websearch.serpapi.key:}")
    private String serpApiKey;

    @Value("${websearch.bing.key:}")
    private String bingApiKey;

    public SearchAggregator(SearchReranker reranker) {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
        this.objectMapper = new ObjectMapper();
        this.reranker = reranker;
    }

    /**
     * 对多个查询并发搜索，去重并评分排序
     */
    public List<SearchResult> aggregateSearch(List<String> queries) {
        if (queries == null || queries.isEmpty()) {
            return List.of();
        }

        List<Mono<List<SearchResult>>> allSearches = new ArrayList<>();
        for (String query : queries) {
            allSearches.add(searchAllSources(query).onErrorReturn(List.of()));
        }

        List<SearchResult> allResults = Flux.merge(allSearches)
                .flatMap(Flux::fromIterable)
                .collectList()
                .block(Duration.ofSeconds(30));

        if (allResults == null || allResults.isEmpty()) {
            return List.of();
        }

        List<SearchResult> deduped = deduplicate(allResults);
        List<SearchResult> scored = reranker.score(deduped);

        log.info("[SearchAggregator] {} queries → {} raw → {} deduped → {} scored",
                queries.size(), allResults.size(), deduped.size(), scored.size());

        return scored;
    }

    /**
     * 单 Query 搜索所有来源
     */
    private Mono<List<SearchResult>> searchAllSources(String query) {
        List<Mono<List<SearchResult>>> sources = new ArrayList<>();

        if (serpApiKey != null && !serpApiKey.isBlank()) {
            sources.add(searchSerpApi(query));
        }

        sources.add(searchDuckDuckGo(query));
        sources.add(searchWikipedia(query));

        if (bingApiKey != null && !bingApiKey.isBlank()) {
            sources.add(searchBing(query));
        }

        return Flux.merge(sources)
                .flatMap(Flux::fromIterable)
                .collectList()
                .onErrorReturn(List.of());
    }

    private Mono<List<SearchResult>> searchSerpApi(String query) {
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

            if (response == null || response.isBlank()) return List.<SearchResult>of();

            JsonNode json = objectMapper.readTree(response);
            List<SearchResult> results = new ArrayList<>();
            JsonNode organic = json.path("organic_results");
            if (organic.isArray()) {
                for (JsonNode item : organic) {
                    if (results.size() >= MAX_RESULTS_PER_SOURCE) break;
                    String title = item.path("title").asText();
                    String snippet = item.path("snippet").asText();
                    String link = item.path("link").asText();
                    if (!title.isBlank()) {
                        results.add(new SearchResult(title, snippet, link, "Google", 0.0));
                    }
                }
            }
            log.info("[SerpAPI] {} → {} results", query, results.size());
            return results;
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    private Mono<List<SearchResult>> searchDuckDuckGo(String query) {
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

            if (response == null || response.isBlank()) return List.<SearchResult>of();

            JsonNode json = objectMapper.readTree(response);
            List<SearchResult> results = new ArrayList<>();

            String absText = json.path("Abstract").asText();
            String absUrl = json.path("AbstractURL").asText();
            if (!absText.isBlank()) {
                results.add(new SearchResult("摘要", absText, absUrl, "DuckDuckGo", 0.0));
            }

            JsonNode related = json.path("RelatedTopics");
            if (related.isArray()) {
                for (JsonNode topic : related) {
                    if (results.size() >= MAX_RESULTS_PER_SOURCE) break;
                    String text = topic.path("Text").asText();
                    String link = topic.path("FirstURL").asText();
                    if (!text.isBlank()) {
                        results.add(new SearchResult("", text, link, "DuckDuckGo", 0.0));
                    }
                }
            }
            return results;
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    private Mono<List<SearchResult>> searchWikipedia(String query) {
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

            if (response == null || response.isBlank()) return List.<SearchResult>of();

            JsonNode json = objectMapper.readTree(response);
            JsonNode sr = json.path("query").path("search");
            List<SearchResult> results = new ArrayList<>();
            if (sr.isArray()) {
                for (JsonNode item : sr) {
                    if (results.size() >= MAX_RESULTS_PER_SOURCE) break;
                    String title = item.path("title").asText();
                    String snippet = item.path("snippet").asText().replaceAll("<[^>]+>", "");
                    if (!title.isBlank()) {
                        results.add(new SearchResult(title, snippet,
                                "https://zh.wikipedia.org/wiki/" + URLEncoder.encode(title, StandardCharsets.UTF_8),
                                "Wikipedia", 0.0));
                    }
                }
            }
            return results;
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    private Mono<List<SearchResult>> searchBing(String query) {
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

            if (response == null || response.isBlank()) return List.<SearchResult>of();

            JsonNode json = objectMapper.readTree(response);
            List<SearchResult> results = new ArrayList<>();
            JsonNode webPages = json.path("webPages").path("value");
            if (webPages.isArray()) {
                for (JsonNode page : webPages) {
                    if (results.size() >= MAX_RESULTS_PER_SOURCE) break;
                    results.add(new SearchResult(
                            page.path("name").asText(),
                            page.path("snippet").asText(),
                            page.path("url").asText(),
                            "Bing", 0.0));
                }
            }
            return results;
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /**
     * 基于域名去重，保留每个域名的第一个结果
     */
    List<SearchResult> deduplicate(List<SearchResult> results) {
        Map<String, SearchResult> unique = new LinkedHashMap<>();
        for (SearchResult r : results) {
            String domain = normalizeDomain(r.url());
            unique.putIfAbsent(domain, r);
        }
        return new ArrayList<>(unique.values());
    }

    private String normalizeDomain(String url) {
        if (url == null || url.isBlank()) return "unknown";
        try {
            String host = url.replaceFirst("^https?://", "")
                    .replaceFirst("/.*$", "")
                    .replaceFirst("^www\\.", "")
                    .toLowerCase();
            return host;
        } catch (Exception e) {
            return url;
        }
    }
}