package com.mcp.tools.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResult(
        String title,
        String snippet,
        String url,
        String source,
        double score
) {
    public SearchResult withScore(double newScore) {
        return new SearchResult(title, snippet, url, source, newScore);
    }

    public SearchResult withSource(String newSource) {
        return new SearchResult(title, snippet, url, newSource, score);
    }
}