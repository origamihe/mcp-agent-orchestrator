package com.mcp.tools.tool.document;

import com.mcp.tools.annotation.McpTool;
import com.mcp.tools.document.parser.DocxParser;
import com.mcp.tools.document.parser.DocumentParser;
import com.mcp.tools.document.parser.PdfParser;
import com.mcp.tools.model.DocumentChunk;
import com.mcp.tools.model.DocumentSearchResult;
import com.mcp.tools.service.WorkspaceFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentSearchToolSet {

    private final WorkspaceFileService fs;
    private final DocxParser docxParser;
    private final PdfParser pdfParser;

    private static final int CONTEXT_CHARS = 100;

    @McpTool(
            name = "search_document",
            description = "Full-text search within a document. Parameters: path (document path), keyword (search term, supports regex), caseSensitive (default false). Returns matching chunks with page numbers and context.",
            tags = {"document", "search", "read"}
    )
    public String searchDocument(String path, String keyword, boolean caseSensitive) {
        try {
            Path filePath = fs.resolve(path);
            if (!Files.exists(filePath)) {
                return fail("File not found: " + path, path, "search_document");
            }
            if (keyword == null || keyword.isBlank()) {
                return fail("Keyword must not be empty", path, "search_document");
            }

            DocumentParser parser = getParser(filePath);
            if (parser == null) {
                return fail("Unsupported file type: " + path, path, "search_document");
            }

            DocumentParser.DocumentParseResult parseResult = parser.parse(filePath);

            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            Pattern pattern;
            try {
                pattern = Pattern.compile(Pattern.quote(keyword), flags);
            } catch (Exception e) {
                pattern = Pattern.compile(keyword, flags);
            }

            List<DocumentSearchResult.Hit> hits = new ArrayList<>();
            for (DocumentChunk chunk : parseResult.chunks()) {
                var matcher = pattern.matcher(chunk.text());
                while (matcher.find()) {
                    int start = Math.max(0, matcher.start() - CONTEXT_CHARS);
                    int end = Math.min(chunk.text().length(), matcher.end() + CONTEXT_CHARS);
                    String context = chunk.text().substring(start, end);

                    if (start > 0) context = "..." + context;
                    if (end < chunk.text().length()) context = context + "...";

                    hits.add(new DocumentSearchResult.Hit(
                            chunk.pageNo(),
                            chunk.section(),
                            chunk.heading(),
                            context,
                            matcher.start(),
                            chunk.sourcePath()
                    ));
                }
            }

            DocumentSearchResult result = new DocumentSearchResult(
                    true, keyword, hits.size(), hits
            );

            log.info("search_document: {} found {} matches for '{}'", path, hits.size(), keyword);
            return success("Found " + hits.size() + " matches for '" + keyword + "'",
                    path, "search_document", toJson(result));

        } catch (Exception e) {
            log.error("Failed to search document: {}", path, e);
            return fail("Search failed: " + e.getMessage(), path, "search_document");
        }
    }

    @McpTool(
            name = "search_document_advanced",
            description = "Advanced search with multiple keywords and filters. Parameters: path (document path), keywords (comma-separated), pageRange (optional, e.g. '1-10'), mode (AND/OR, default OR).",
            tags = {"document", "search", "advanced"}
    )
    public String searchDocumentAdvanced(String path, String keywords, String pageRange, String mode) {
        try {
            Path filePath = fs.resolve(path);
            if (!Files.exists(filePath)) {
                return fail("File not found: " + path, path, "search_document_advanced");
            }

            DocumentParser parser = getParser(filePath);
            if (parser == null) {
                return fail("Unsupported file type: " + path, path, "search_document_advanced");
            }

            DocumentParser.DocumentParseResult parseResult = parser.parse(filePath);

            String[] keywordArray = keywords.split(",");
            for (int i = 0; i < keywordArray.length; i++) {
                keywordArray[i] = keywordArray[i].trim();
            }

            boolean andMode = "AND".equalsIgnoreCase(mode);
            int startPage = 1, endPage = parseResult.totalPages();
            if (pageRange != null && pageRange.contains("-")) {
                String[] parts = pageRange.split("-");
                startPage = Integer.parseInt(parts[0].trim());
                endPage = Integer.parseInt(parts[1].trim());
            }

            List<DocumentSearchResult.Hit> hits = new ArrayList<>();
            for (DocumentChunk chunk : parseResult.chunks()) {
                if (chunk.pageNo() < startPage || chunk.pageNo() > endPage) continue;

                boolean matched = andMode;
                for (String kw : keywordArray) {
                    boolean found = chunk.text().toLowerCase().contains(kw.toLowerCase());
                    if (andMode) {
                        matched = matched && found;
                    } else {
                        matched = matched || found;
                    }
                }

                if (matched) {
                    String context = chunk.text();
                    if (context.length() > CONTEXT_CHARS * 3) {
                        context = context.substring(0, CONTEXT_CHARS * 3) + "...";
                    }
                    hits.add(new DocumentSearchResult.Hit(
                            chunk.pageNo(), chunk.section(), chunk.heading(),
                            context, 0, chunk.sourcePath()
                    ));
                }
            }

            DocumentSearchResult result = new DocumentSearchResult(true, keywords, hits.size(), hits);
            return success("Found " + hits.size() + " matching chunks",
                    path, "search_document_advanced", toJson(result));

        } catch (Exception e) {
            log.error("Advanced search failed: {}", path, e);
            return fail("Search failed: " + e.getMessage(), path, "search_document_advanced");
        }
    }

    private DocumentParser getParser(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".docx")) return docxParser;
        if (fileName.endsWith(".pdf")) return pdfParser;
        return null;
    }

    private String success(String message, String path, String operation, String data) {
        return "{\"ok\":true,\"tool\":\"" + operation + "\",\"path\":\"" +
                escapeJson(path) + "\",\"message\":\"" + escapeJson(message) +
                "\",\"data\":" + data + "}";
    }

    private String fail(String message, String path, String operation) {
        return "{\"ok\":false,\"tool\":\"" + operation + "\",\"path\":\"" +
                escapeJson(path) + "\",\"error\":\"" + escapeJson(message) + "\"}";
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "\"serialization error\"";
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}