package com.mcp.tools.document.parser;

import com.mcp.tools.model.DocumentChunk;
import java.nio.file.Path;
import java.util.List;

public interface DocumentParser {

    String supportedType();

    DocumentParseResult parse(Path filePath) throws Exception;

    record DocumentParseResult(
            List<DocumentChunk> chunks,
            int totalPages,
            boolean isScanned,
            java.util.Map<String, String> metadata
    ) {}
}