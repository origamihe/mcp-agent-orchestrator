package com.mcp.tools.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentTable(
        int pageNo,
        String section,
        String caption,
        List<String> headers,
        List<List<String>> rows,
        int rowCount,
        int colCount
) {}