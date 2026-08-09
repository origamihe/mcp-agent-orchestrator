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
        // ===== 技术类高权重域名 =====
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
        DOMAIN_WEIGHTS.put("apache.org", 8.0);
        DOMAIN_WEIGHTS.put("python.org", 8.0);
        DOMAIN_WEIGHTS.put("openai.com", 7.0);
        DOMAIN_WEIGHTS.put("anthropic.com", 7.0);
        DOMAIN_WEIGHTS.put("arxiv.org", 8.0);
        DOMAIN_WEIGHTS.put("reddit.com", 5.0);
        DOMAIN_WEIGHTS.put("news.ycombinator.com", 6.0);

        // ===== 国际权威新闻通讯社 =====
        DOMAIN_WEIGHTS.put("reuters.com", 9.8);
        DOMAIN_WEIGHTS.put("apnews.com", 9.7);
        DOMAIN_WEIGHTS.put("bbc.com", 9.5);
        DOMAIN_WEIGHTS.put("bbc.co.uk", 9.5);
        DOMAIN_WEIGHTS.put("economist.com", 9.4);
        DOMAIN_WEIGHTS.put("bloomberg.com", 9.0);
        DOMAIN_WEIGHTS.put("ft.com", 9.0);
        DOMAIN_WEIGHTS.put("wsj.com", 8.5);
        DOMAIN_WEIGHTS.put("nytimes.com", 8.0);
        DOMAIN_WEIGHTS.put("washingtonpost.com", 7.5);
        DOMAIN_WEIGHTS.put("theguardian.com", 7.5);
        DOMAIN_WEIGHTS.put("aljazeera.com", 7.0);
        DOMAIN_WEIGHTS.put("cnn.com", 7.0);
        DOMAIN_WEIGHTS.put("npr.org", 7.0);
        DOMAIN_WEIGHTS.put("pbs.org", 7.0);

        // ===== 学术/科研权威来源 =====
        DOMAIN_WEIGHTS.put("nature.com", 9.5);
        DOMAIN_WEIGHTS.put("science.org", 9.5);
        DOMAIN_WEIGHTS.put("sciencedirect.com", 9.0);
        DOMAIN_WEIGHTS.put("thelancet.com", 9.5);
        DOMAIN_WEIGHTS.put("nejm.org", 9.5);
        DOMAIN_WEIGHTS.put("cell.com", 9.0);
        DOMAIN_WEIGHTS.put("pnas.org", 9.0);
        DOMAIN_WEIGHTS.put("ieee.org", 8.5);
        DOMAIN_WEIGHTS.put("acm.org", 8.5);
        DOMAIN_WEIGHTS.put("scholar.google.com", 8.0);
        DOMAIN_WEIGHTS.put("researchgate.net", 7.0);

        // ===== 国际组织 =====
        DOMAIN_WEIGHTS.put("who.int", 9.0);
        DOMAIN_WEIGHTS.put("un.org", 9.0);
        DOMAIN_WEIGHTS.put("worldbank.org", 8.5);
        DOMAIN_WEIGHTS.put("imf.org", 8.5);
        DOMAIN_WEIGHTS.put("oecd.org", 8.5);
        DOMAIN_WEIGHTS.put("nato.int", 8.0);
        DOMAIN_WEIGHTS.put("europa.eu", 8.5);

        // ===== 中国政府及官方来源 =====
        DOMAIN_WEIGHTS.put("gov.cn", 8.5);
        DOMAIN_WEIGHTS.put("fmprc.gov.cn", 9.0);
        DOMAIN_WEIGHTS.put("mfa.gov.cn", 9.0);
        DOMAIN_WEIGHTS.put("mod.gov.cn", 8.5);
        DOMAIN_WEIGHTS.put("xinhuanet.com", 8.0);
        DOMAIN_WEIGHTS.put("people.com.cn", 7.5);
        DOMAIN_WEIGHTS.put("cctv.com", 7.5);
        DOMAIN_WEIGHTS.put("chinadaily.com.cn", 7.5);
        DOMAIN_WEIGHTS.put("cankaoxiaoxi.com", 7.0);

        // ===== 中等权重：综合门户/科技媒体 =====
        DOMAIN_WEIGHTS.put("medium.com", 4.0);
        DOMAIN_WEIGHTS.put("dev.to", 4.0);
        DOMAIN_WEIGHTS.put("segmentfault.com", 4.0);
        DOMAIN_WEIGHTS.put("infoq.com", 5.0);
        DOMAIN_WEIGHTS.put("techcrunch.com", 5.0);
        DOMAIN_WEIGHTS.put("wired.com", 5.0);
        DOMAIN_WEIGHTS.put("theverge.com", 5.0);
        DOMAIN_WEIGHTS.put("arstechnica.com", 6.0);

        // ===== 低权重：自媒体/论坛 =====
        DOMAIN_WEIGHTS.put("blog.csdn.net", 3.0);
        DOMAIN_WEIGHTS.put("juejin.cn", 3.0);
        DOMAIN_WEIGHTS.put("zhihu.com", 2.0);
        DOMAIN_WEIGHTS.put("weibo.com", 1.0);
        DOMAIN_WEIGHTS.put("mp.weixin.qq.com", 1.5);
        DOMAIN_WEIGHTS.put("toutiao.com", 2.0);
        DOMAIN_WEIGHTS.put("163.com", 2.5);
        DOMAIN_WEIGHTS.put("sina.com.cn", 2.5);
        DOMAIN_WEIGHTS.put("sohu.com", 2.0);
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