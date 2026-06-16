package com.mcp.tools.search;

import com.mcp.tools.model.SearchDocument;
import com.mcp.tools.model.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ContentFetcher {

    private final WebClient webClient;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_CONTENT_LENGTH = 4000;
    private static final int MAX_FETCH_COUNT = 3;

    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "<title[^>]*>(.*?)</title>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    public ContentFetcher() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    /**
     * 对排名靠前的搜索结果自动抓取网页正文
     */
    public List<SearchDocument> fetchTopResults(List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        List<SearchResult> topResults = results.stream()
                .limit(MAX_FETCH_COUNT)
                .toList();

        List<CompletableFuture<SearchDocument>> futures = topResults.stream()
                .map(r -> CompletableFuture.supplyAsync(() -> fetchContent(r)))
                .toList();

        List<SearchDocument> documents = new ArrayList<>();
        for (CompletableFuture<SearchDocument> future : futures) {
            try {
                SearchDocument doc = future.get(REQUEST_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                if (doc != null && doc.content() != null && !doc.content().isBlank()) {
                    documents.add(doc);
                }
            } catch (Exception e) {
                log.debug("[ContentFetcher] Fetch failed: {}", e.getMessage());
            }
        }

        log.info("[ContentFetcher] Fetched {}/{} pages", documents.size(), topResults.size());
        return documents;
    }

    private SearchDocument fetchContent(SearchResult result) {
        String url = result.url();
        if (url == null || url.isBlank()) return null;

        try {
            String html = webClient.get()
                    .uri(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .onErrorReturn("")
                    .block(REQUEST_TIMEOUT);

            if (html == null || html.isBlank()) return null;

            String text = extractText(html);
            if (text.isBlank()) return null;

            if (text.length() > MAX_CONTENT_LENGTH) {
                text = text.substring(0, MAX_CONTENT_LENGTH) + "...";
            }

            String title = extractTitle(html);
            if (title.isBlank()) {
                title = result.title();
            }

            log.debug("[ContentFetcher] Fetched: {} ({} chars)", url, text.length());
            return new SearchDocument(title, url, text, text.length());

        } catch (Exception e) {
            log.debug("[ContentFetcher] Error fetching {}: {}", url, e.getMessage());
            return null;
        }
    }

    private String extractTitle(String html) {
        Matcher m = TITLE_PATTERN.matcher(html);
        if (m.find()) {
            return m.group(1).replaceAll("\\s+", " ").trim();
        }
        return "";
    }

    private String extractText(String html) {
        String text = html
                .replaceAll("(?si)<head[^>]*>.*?</head>", " ")
                .replaceAll("(?si)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?si)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?si)<noscript[^>]*>.*?</noscript>", " ")
                .replaceAll("(?si)<svg[^>]*>.*?</svg>", " ")
                .replaceAll("(?si)<iframe[^>]*>.*?</iframe>", " ")
                .replaceAll("(?si)<!--.*?-->", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#?[a-z0-9]+;", " ")
                .replaceAll("[\\t\\r]+", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\\n{3,}", "\n\n");

        StringBuilder result = new StringBuilder();
        for (String line : text.split("\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && trimmed.length() > 2) {
                result.append(trimmed).append("\n");
            }
        }
        return result.toString().trim();
    }
}