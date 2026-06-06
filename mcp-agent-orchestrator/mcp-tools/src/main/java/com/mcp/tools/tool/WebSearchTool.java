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
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class WebSearchTool {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_RESULTS = 5;

    @Value("${websearch.serpapi.key:}")
    private String serpApiKey;

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
    public String webSearch(String query) {
        if (query == null || query.isBlank()) {
            return "错误：未提供搜索关键词，请提供有效的 query 参数。";
        }

        log.info("Starting web search for query: {}", query);

        // 策略1：如果配置了 SerpAPI Key，优先使用（付费服务，最稳定）
        if (serpApiKey != null && !serpApiKey.isBlank()) {
            String result = searchWithSerpApi(query);
            if (result != null) {
                return result;
            }
            log.warn("SerpAPI 搜索失败，降级到 DuckDuckGo...");
        }

        // 策略2：DuckDuckGo 匿名搜索
        String result = searchWithDuckDuckGo(query);
        if (result != null) {
            return result;
        }

        // 策略3：Wikipedia API 作为最后备选
        log.warn("DuckDuckGo 搜索失败，降级到 Wikipedia...");
        return searchWithWikipedia(query);
    }

    private String searchWithSerpApi(String query) {
        try {
            String url = "https://serpapi.com/search?q=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8) +
                    "&api_key=" + serpApiKey +
                    "&engine=google&num=" + MAX_RESULTS;

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .retryWhen(Retry.max(2))
                    .block(REQUEST_TIMEOUT);

            if (response == null || response.isBlank()) {
                return null;
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
                return null;
            }
            log.info("SerpAPI 搜索完成: {} ({} 条结果)", query, results.size());
            return "搜索关键词: " + query + "\n数据来源: Google (SerpAPI)\n\n" + String.join("\n\n", results);
        } catch (Exception e) {
            log.warn("SerpAPI 搜索异常: {}", e.getMessage());
            return null;
        }
    }

    private String searchWithDuckDuckGo(String query) {
        try {
            String url = "https://api.duckduckgo.com/?q=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8) +
                    "&format=json&no_html=1&skip_disambig=1";

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .retryWhen(Retry.max(2))
                    .doOnError(e -> log.warn("DuckDuckGo 请求错误: {}", e.getMessage()))
                    .onErrorReturn("")
                    .block(REQUEST_TIMEOUT);

            if (response == null || response.isBlank()) {
                return null;
            }

            return parseDuckDuckGoResponse(query, response);
        } catch (Exception e) {
            log.warn("DuckDuckGo 搜索异常: {}", e.getMessage());
            return null;
        }
    }

    private String parseDuckDuckGoResponse(String query, String response) throws Exception {
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
            return null;
        }

        log.info("DuckDuckGo 搜索完成: {} ({} 条结果)", query, results.size());
        return "搜索关键词: " + query + "\n数据来源: DuckDuckGo\n\n" + String.join("\n\n", results);
    }

    private String searchWithWikipedia(String query) {
        try {
            String url = "https://zh.wikipedia.org/w/api.php?action=query&list=search&srsearch=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8) +
                    "&format=json&srlimit=" + MAX_RESULTS;

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .doOnError(e -> log.warn("Wikipedia 请求错误: {}", e.getMessage()))
                    .onErrorReturn("")
                    .block(REQUEST_TIMEOUT);

            if (response == null || response.isBlank()) {
                return "搜索失败：所有搜索渠道（SerpAPI、DuckDuckGo、Wikipedia）均无法访问。\n" +
                       "建议：\n" +
                       "1. 检查网络连接是否正常\n" +
                       "2. 确认是否需要配置代理\n" +
                       "3. 可在 application.yml 中配置 websearch.serpapi.key 使用付费搜索 API";
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
                return "搜索关键词: " + query + "\n\n所有搜索渠道均未返回有效结果，建议更换关键词重试。";
            }

            log.info("Wikipedia 搜索完成: {} ({} 条结果)", query, results.size());
            return "搜索关键词: " + query + "\n数据来源: Wikipedia\n\n" + String.join("\n\n", results);
        } catch (Exception e) {
            log.error("所有搜索方式均失败，query: {}", query, e);
            return "搜索失败：所有搜索渠道均不可用。\n" +
                   "错误详情: " + e.getMessage() + "\n" +
                   "建议检查网络连接或稍后重试。";
        }
    }
}