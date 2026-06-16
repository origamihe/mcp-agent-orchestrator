package com.mcp.tools.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mcp.tools.annotation.McpTool;
import com.mcp.tools.model.SearchDocument;
import com.mcp.tools.model.SearchResult;
import com.mcp.tools.search.ContentFetcher;
import com.mcp.tools.search.QueryExpander;
import com.mcp.tools.search.SearchAggregator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DeepResearchTool {

    private final QueryExpander queryExpander;
    private final SearchAggregator searchAggregator;
    private final ContentFetcher contentFetcher;
    private final ObjectMapper objectMapper;

    private static final int MAX_DEEP_SEARCH_ROUNDS = 2;

    public DeepResearchTool(QueryExpander queryExpander,
                            SearchAggregator searchAggregator,
                            ContentFetcher contentFetcher) {
        this.queryExpander = queryExpander;
        this.searchAggregator = searchAggregator;
        this.contentFetcher = contentFetcher;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @McpTool(
            name = "deep_research",
            description = "深度研究工具：自动扩展搜索词、多源并发搜索、去重排序、自动抓取网页正文。"
                    + "参数 query：研究主题/问题。参数 depth：搜索深度（1=基础搜索，2=递归深度搜索，默认1）。",
            tags = {"search", "research", "deep", "agent"}
    )
    public String deepResearch(String query, String depth) {
        if (query == null || query.isBlank()) {
            return "{\"error\": \"未提供搜索关键词\"}";
        }

        int searchDepth = parseDepth(depth);
        log.info("[DeepResearch] Starting research: query='{}', depth={}", query, searchDepth);

        try {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("query", query);
            report.put("depth", searchDepth);

            // Round 1: 多 Query 搜索 + 去重 + 评分 + 抓取正文
            List<String> queries = queryExpander.expand(query);
            List<SearchResult> results = searchAggregator.aggregateSearch(queries);
            List<SearchDocument> documents = contentFetcher.fetchTopResults(results);

            report.put("expandedQueries", queries);
            report.put("resultCount", results.size());
            report.put("results", results);
            report.put("documents", documents);

            // Round 2: 递归深度搜索（从搜索结果中提取子主题再搜）
            if (searchDepth >= 2 && !results.isEmpty()) {
                List<String> subTopics = extractSubTopics(results, documents);
                if (!subTopics.isEmpty()) {
                    log.info("[DeepResearch] Round 2: searching {} sub-topics", subTopics.size());
                    List<SearchResult> round2Results = searchAggregator.aggregateSearch(subTopics);
                    List<SearchDocument> round2Docs = contentFetcher.fetchTopResults(round2Results);

                    report.put("round2", Map.of(
                            "subTopics", subTopics,
                            "resultCount", round2Results.size(),
                            "results", round2Results,
                            "documents", round2Docs
                    ));
                }
            }

            report.put("summary", buildSummary(results, documents, searchDepth));

            String json = objectMapper.writeValueAsString(report);
            log.info("[DeepResearch] Complete: {} results, {} docs", results.size(), documents.size());
            return json;

        } catch (Exception e) {
            log.error("[DeepResearch] Failed: {}", e.getMessage(), e);
            return "{\"error\": \"深度研究失败: " + e.getMessage() + "\", \"query\": \"" + query + "\"}";
        }
    }

    private int parseDepth(String depth) {
        if (depth == null || depth.isBlank()) return 1;
        try {
            int d = Integer.parseInt(depth.trim());
            return Math.max(1, Math.min(d, MAX_DEEP_SEARCH_ROUNDS));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * 从搜索结果和文档中提取子主题用于递归搜索
     */
    private List<String> extractSubTopics(List<SearchResult> results, List<SearchDocument> documents) {
        Set<String> topics = new LinkedHashSet<>();

        // 从标题中提取关键词组合
        for (SearchResult r : results) {
            String title = r.title();
            if (title != null && !title.isBlank()) {
                String[] keywords = extractKeywords(title);
                for (String kw : keywords) {
                    if (kw.length() >= 4 && !topics.contains(kw)) {
                        topics.add(kw);
                    }
                }
            }
        }

        // 从文档正文中提取高频关键词
        for (SearchDocument doc : documents) {
            if (doc.content() != null) {
                String[] keywords = extractKeywords(doc.content());
                int added = 0;
                for (String kw : keywords) {
                    if (kw.length() >= 4 && topics.add(kw)) {
                        added++;
                        if (added >= 3) break;
                    }
                }
            }
        }

        return topics.stream().limit(5).collect(Collectors.toList());
    }

    /**
     * 简单关键词提取：按常见分隔符拆分，取长度合适的词
     */
    private String[] extractKeywords(String text) {
        if (text == null || text.isBlank()) return new String[0];
        return text.replaceAll("[\\[\\]()（）「」『』\"'`,，。；;：:！!？?\\-–—/\\\\|@#$%^&*+=<>{}~]+", " ")
                .split("\\s+");
    }

    private String buildSummary(List<SearchResult> results, List<SearchDocument> documents, int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append("搜索完成，共找到 ").append(results.size()).append(" 条结果");
        if (!documents.isEmpty()) {
            sb.append("，已抓取 ").append(documents.size()).append(" 篇网页正文");
        }
        if (depth >= 2) {
            sb.append("（含递归深度搜索）");
        }
        sb.append("。");
        return sb.toString();
    }
}