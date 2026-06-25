package com.mcp.tools.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentSearchResult(
        boolean success,
        String keyword,
        int totalMatches,
        List<Hit> hits
) {
    public record Hit(
            int pageNo,
            String section,
            String heading,
            String context,
            int matchOffset,
            String sourcePath
    ) {}
}