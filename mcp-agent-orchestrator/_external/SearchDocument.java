package com.mcp.tools.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchDocument(
        String title,
        String url,
        String content,
        int contentLength
) {
    public SearchDocument {
        if (content != null) {
            contentLength = content.length();
        }
    }
}