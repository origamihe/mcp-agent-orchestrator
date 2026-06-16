package com.mcp.tools.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
public class QueryExpander {

    private static final int MAX_EXPANDED_QUERIES = 4;

    private static final Map<Pattern, List<String>> EXPANSION_RULES = new LinkedHashMap<>();

    static {
        EXPANSION_RULES.put(Pattern.compile(".*(最新|新特性|更新|changelog|release).*", Pattern.CASE_INSENSITIVE),
                List.of(" release notes", " changelog", " latest version", " new features"));

        EXPANSION_RULES.put(Pattern.compile(".*(教程|怎么用|如何使用|入门|guide|tutorial|getting.?started).*", Pattern.CASE_INSENSITIVE),
                List.of(" tutorial", " guide", " getting started", " documentation", " 入门教程"));

        EXPANSION_RULES.put(Pattern.compile(".*(API|接口|文档|doc|reference).*", Pattern.CASE_INSENSITIVE),
                List.of(" API documentation", " API reference", " developer guide", " official docs"));

        EXPANSION_RULES.put(Pattern.compile(".*(报错|错误|bug|error|异常|exception|问题|解决|修复|fix).*", Pattern.CASE_INSENSITIVE),
                List.of(" error solution", " fix", " troubleshooting", " stackoverflow", " github issue"));

        EXPANSION_RULES.put(Pattern.compile(".*(对比|vs|区别|比较|哪个好|选择).*", Pattern.CASE_INSENSITIVE),
                List.of(" vs ", " comparison", " pros and cons", " difference"));
    }

    private static final List<String> DEFAULT_SUFFIXES = List.of(
            " documentation", " GitHub", " tutorial", " overview"
    );

    public List<String> expand(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<String> queries = new ArrayList<>();
        queries.add(query.trim());

        for (Map.Entry<Pattern, List<String>> entry : EXPANSION_RULES.entrySet()) {
            if (entry.getKey().matcher(query).find()) {
                for (String suffix : entry.getValue()) {
                    if (queries.size() >= MAX_EXPANDED_QUERIES) break;
                    String expanded = query.trim() + suffix;
                    if (!queries.contains(expanded)) {
                        queries.add(expanded);
                    }
                }
                break;
            }
        }

        if (queries.size() < MAX_EXPANDED_QUERIES) {
            for (String suffix : DEFAULT_SUFFIXES) {
                if (queries.size() >= MAX_EXPANDED_QUERIES) break;
                String expanded = query.trim() + suffix;
                if (!queries.contains(expanded)) {
                    queries.add(expanded);
                }
            }
        }

        log.info("[QueryExpander] {} → {} queries: {}", query, queries.size(), queries);
        return queries;
    }
}