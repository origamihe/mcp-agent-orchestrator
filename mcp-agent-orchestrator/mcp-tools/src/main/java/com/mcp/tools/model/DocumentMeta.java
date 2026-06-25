package com.mcp.tools.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentMeta(
        boolean success,
        String fileType,
        String fileName,
        String sourcePath,
        int pages,
        long fileSize,
        String createdAt,
        boolean isScanned,
        boolean isOcr,
        List<Section> outline,
        Map<String, String> metadata
) {
    public record Section(
            int level,
            String title,
            int pageNo
    ) {}
}