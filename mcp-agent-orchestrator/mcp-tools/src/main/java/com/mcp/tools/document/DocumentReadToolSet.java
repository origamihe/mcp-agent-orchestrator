package com.mcp.tools.tool.document;

import com.mcp.tools.annotation.McpTool;
import com.mcp.tools.document.chunker.DocumentChunker;
import com.mcp.tools.document.normalizer.DocumentNormalizer;
import com.mcp.tools.document.parser.DocxParser;
import com.mcp.tools.document.parser.DocumentParser;
import com.mcp.tools.document.parser.PdfParser;
import com.mcp.tools.model.DocumentChunk;
import com.mcp.tools.model.DocumentMeta;
import com.mcp.tools.model.DocumentTable;
import com.mcp.tools.service.WorkspaceFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentReadToolSet {

    private final WorkspaceFileService fs;
    private final DocxParser docxParser;
    private final PdfParser pdfParser;
    private final DocumentNormalizer normalizer;
    private final DocumentChunker chunker;

    @McpTool(
            name = "read_document_meta",
            description = "Get document metadata: file type, pages, outline, whether scanned, etc. Parameters: path (document path). Supports .docx and .pdf.",
            tags = {"document", "read", "metadata"}
    )
    public String readDocumentMeta(String path) {
        try {
            Path filePath = fs.resolve(path);
            if (!Files.exists(filePath)) {
                return fail("File not found: " + path, path, "read_document_meta");
            }

            DocumentParser parser = getParser(filePath);
            if (parser == null) {
                return fail("Unsupported file type. Only .docx and .pdf are supported: " + path,
                        path, "read_document_meta");
            }

            DocumentParser.DocumentParseResult parseResult = parser.parse(filePath);
            String fileType = parser.supportedType();

            List<DocumentMeta.Section> outline = new ArrayList<>();
            for (DocumentChunk chunk : parseResult.chunks()) {
                if (chunk.heading() != null) {
                    int level = 1;
                    if (chunk.section() != null && !chunk.section().equals(chunk.heading())) {
                        level = 2;
                    }
                    outline.add(new DocumentMeta.Section(level, chunk.heading(), chunk.pageNo()));
                }
            }

            DocumentMeta meta = new DocumentMeta(
                    true, fileType, filePath.getFileName().toString(),
                    filePath.toString(), parseResult.totalPages(),
                    Files.size(filePath),
                    parseResult.metadata().getOrDefault("createdAt", "unknown"),
                    parseResult.isScanned(), false,
                    outline, parseResult.metadata()
            );

            return success("Document metadata retrieved", path, "read_document_meta", toJson(meta));

        } catch (Exception e) {
            log.error("Failed to read document meta: {}", path, e);
            return fail("Failed to read document: " + e.getMessage(), path, "read_document_meta");
        }
    }

    @McpTool(
            name = "read_document_range",
            description = "Read a page range of a document. Parameters: path (document path), startPage (1-based), endPage (1-based, -1 for last page). Returns structured chunks with page/section info.",
            tags = {"document", "read", "range"}
    )
    public String readDocumentRange(String path, int startPage, int endPage) {
        try {
            Path filePath = fs.resolve(path);
            if (!Files.exists(filePath)) {
                return fail("File not found: " + path, path, "read_document_range");
            }

            DocumentParser parser = getParser(filePath);
            if (parser == null) {
                return fail("Unsupported file type: " + path, path, "read_document_range");
            }

            DocumentParser.DocumentParseResult parseResult = parser.parse(filePath);
            int totalPages = parseResult.totalPages();

            if (startPage < 1) startPage = 1;
            if (endPage < 0 || endPage > totalPages) endPage = totalPages;
            if (endPage < startPage) {
                return fail("endPage must be >= startPage", path, "read_document_range");
            }

            int finalStart = startPage;
            int finalEnd = endPage;
            List<DocumentChunk> filtered = parseResult.chunks().stream()
                    .filter(c -> c.pageNo() >= finalStart && c.pageNo() <= finalEnd)
                    .collect(Collectors.toList());

            String structured = normalizer.toStructuredJson(filtered, path, parser.supportedType());

            log.info("read_document_range: {} pages {}-{}/{} ({} chunks)",
                    path, startPage, endPage, totalPages, filtered.size());

            return success("Range read: pages " + startPage + "-" + endPage + " of " + totalPages,
                    path, "read_document_range", structured);

        } catch (Exception e) {
            log.error("Failed to read document range: {}", path, e);
            return fail("Failed to read range: " + e.getMessage(), path, "read_document_range");
        }
    }

    @McpTool(
            name = "preview_document_page",
            description = "Preview a single page of a document. Parameters: path (document path), pageNo (1-based). Returns text content of that page.",
            tags = {"document", "read", "preview"}
    )
    public String previewDocumentPage(String path, int pageNo) {
        return readDocumentRange(path, pageNo, pageNo);
    }

    @McpTool(
            name = "extract_document_tables",
            description = "Extract all tables from a document. Parameters: path (document path), pageNo (optional, 0 for all pages). Returns structured table data.",
            tags = {"document", "read", "table"}
    )
    public String extractDocumentTables(String path, int pageNo) {
        try {
            Path filePath = fs.resolve(path);
            if (!Files.exists(filePath)) {
                return fail("File not found: " + path, path, "extract_document_tables");
            }

            DocumentParser parser = getParser(filePath);
            if (parser == null) {
                return fail("Unsupported file type: " + path, path, "extract_document_tables");
            }

            DocumentParser.DocumentParseResult parseResult = parser.parse(filePath);

            List<DocumentTable> tables = new ArrayList<>();
            for (DocumentChunk chunk : parseResult.chunks()) {
                if (chunk.tables() != null) {
                    for (DocumentTable table : chunk.tables()) {
                        if (pageNo == 0 || table.pageNo() == pageNo) {
                            tables.add(table);
                        }
                    }
                }
            }

            String result = toJson(tables);
            return success("Tables extracted: " + tables.size(), path, "extract_document_tables", result);

        } catch (Exception e) {
            log.error("Failed to extract tables: {}", path, e);
            return fail("Failed to extract tables: " + e.getMessage(), path, "extract_document_tables");
        }
    }

    @McpTool(
            name = "extract_document_outline",
            description = "Extract document outline/heading tree. Parameters: path (document path). Returns hierarchical heading structure with page numbers.",
            tags = {"document", "read", "outline"}
    )
    public String extractDocumentOutline(String path) {
        try {
            Path filePath = fs.resolve(path);
            if (!Files.exists(filePath)) {
                return fail("File not found: " + path, path, "extract_document_outline");
            }

            DocumentParser parser = getParser(filePath);
            if (parser == null) {
                return fail("Unsupported file type: " + path, path, "extract_document_outline");
            }

            DocumentParser.DocumentParseResult parseResult = parser.parse(filePath);

            List<Map<String, Object>> outline = new ArrayList<>();
            for (DocumentChunk chunk : parseResult.chunks()) {
                if (chunk.heading() != null) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("title", chunk.heading());
                    item.put("page", chunk.pageNo());
                    item.put("source", chunk.sourcePath());
                    outline.add(item);
                }
            }

            return success("Outline extracted: " + outline.size() + " headings",
                    path, "extract_document_outline", toJson(outline));

        } catch (Exception e) {
            log.error("Failed to extract outline: {}", path, e);
            return fail("Failed to extract outline: " + e.getMessage(), path, "extract_document_outline");
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