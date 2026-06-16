package com.mcp.tools.search;

import com.mcp.tools.model.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SearchReranker {

    private static final Map<String, Double> DOMAIN_WEIGHTS = new LinkedHashMap<>();

    static {
        DOMAIN_WEIGHTS.put("oracle.com", 10.0);
        DOMAIN_WEIGHTS.put("spring.io", 10.0);
        DOMAIN_WEIGHTS.put("docs.spring.io", 10.0);
        DOMAIN_WEIGHTS.put("github.com", 9.0);
        DOMAIN_WEIGHTS.put("stackoverflow.com", 8.0);
        DOMAIN_WEIGHTS.put("wikipedia.org", 8.0);
        DOMAIN_WEIGHTS.put("developer.mozilla.org", 8.0);
        DOMAIN_WEIGHTS.put("aws.amazon.com", 7.0);
        DOMAIN_WEIGHTS.put("learn.microsoft.com", 7.0);
        DOMAIN_WEIGHTS.put("docs.oracle.com", 7.0);
        DOMAIN_WEIGHTS.put("baeldung.com", 7.0);
        DOMAIN_WEIGHTS.put("medium.com", 4.0);
        DOMAIN_WEIGHTS.put("dev.to", 4.0);
        DOMAIN_WEIGHTS.put("blog.csdn.net", 3.0);
        DOMAIN_WEIGHTS.put("juejin.cn", 3.0);
        DOMAIN_WEIGHTS.put("zhihu.com", 3.0);
        DOMAIN_WEIGHTS.put("segmentfault.com", 4.0);
        DOMAIN_WEIGHTS.put("arxiv.org", 8.0);
        DOMAIN_WEIGHTS.put("reddit.com", 5.0);
        DOMAIN_WEIGHTS.put("news.ycombinator.com", 6.0);
        DOMAIN_WEIGHTS.put("apache.org", 8.0);
        DOMAIN_WEIGHTS.put("python.org", 8.0);
        DOMAIN_WEIGHTS.put("openai.com", 7.0);
        DOMAIN_WEIGHTS.put("anthropic.com", 7.0);
    }

    private static final double DEFAULT_WEIGHT = 5.0;
    private static final double SNIPPET_LENGTH_BONUS = 2.0;
    private static final int MIN_SNIPPET_LENGTH = 50;

    /**
     * 对搜索结果进行评分排序
     * 评分 = 域名权重 + snippet 长度加分
     */
    public List<SearchResult> score(List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        List<SearchResult> scored = results.stream()
                .map(this::computeScore)
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .collect(Collectors.toList());

        double topScore = scored.isEmpty() ? 0 : scored.get(0).score();
        log.info("[Reranker] Scored {} results, top score: {}", scored.size(), topScore);

        return scored;
    }

    private SearchResult computeScore(SearchResult result) {
        double score = getDomainWeight(result.url());

        if (result.snippet() != null && result.snippet().length() > MIN_SNIPPET_LENGTH) {
            score += SNIPPET_LENGTH_BONUS;
        }

        return result.withScore(Math.round(score * 10.0) / 10.0);
    }

    private double getDomainWeight(String url) {
        if (url == null || url.isBlank()) return DEFAULT_WEIGHT;

        String host = url.replaceFirst("^https?://", "")
                .replaceFirst("/.*$", "")
                .toLowerCase();

        for (Map.Entry<String, Double> entry : DOMAIN_WEIGHTS.entrySet()) {
            if (host.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return DEFAULT_WEIGHT;
    }
}