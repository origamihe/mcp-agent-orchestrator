package com.mcp.tools.document.parser;

import com.mcp.tools.model.DocumentChunk;
import com.mcp.tools.model.DocumentTable;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
public class DocxParser implements DocumentParser {

    @Override
    public String supportedType() {
        return "docx";
    }

    @Override
    public DocumentParseResult parse(Path filePath) throws Exception {
        List<DocumentChunk> chunks = new ArrayList<>();
        Map<String, String> metadata = new LinkedHashMap<>();
        String sourcePath = filePath.toString();

        BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
        metadata.put("createdAt", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(attrs.creationTime().toMillis())));

        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(filePath))) {
            metadata.put("paragraphs", String.valueOf(doc.getParagraphs().size()));
            metadata.put("tables", String.valueOf(doc.getTables().size()));

            int pageNo = 1;
            int offset = 0;
            String currentSection = null;
            int currentLevel = 0;

            List<IBodyElement> bodyElements = doc.getBodyElements();
            for (IBodyElement element : bodyElements) {
                switch (element.getElementType()) {
                    case PARAGRAPH -> {
                        XWPFParagraph para = (XWPFParagraph) element;
                        String style = para.getStyle() != null ? para.getStyle() : "";

                        if (style != null && style.toLowerCase().contains("heading")) {
                            int level = extractHeadingLevel(style);
                            String headingText = para.getText().trim();
                            if (!headingText.isEmpty()) {
                                if (level <= currentLevel || currentLevel == 0) {
                                    currentSection = headingText;
                                }
                                currentLevel = level;
                                DocumentChunk chunk = new DocumentChunk(
                                        sourcePath + "#h" + offset, pageNo,
                                        currentSection, headingText, headingText,
                                        null, null, offset, sourcePath, "docx"
                                );
                                chunks.add(chunk);
                            }
                        } else {
                            String text = para.getText().trim();
                            if (!text.isEmpty()) {
                                DocumentChunk chunk = new DocumentChunk(
                                        sourcePath + "#p" + offset, pageNo,
                                        currentSection, null, text,
                                        null, null, offset, sourcePath, "docx"
                                );
                                chunks.add(chunk);
                            }
                        }
                        offset++;
                    }
                    case TABLE -> {
                        XWPFTable table = (XWPFTable) element;
                        List<DocumentTable> tables = extractTables(table, pageNo, currentSection, sourcePath);
                        if (!tables.isEmpty()) {
                            StringBuilder tableText = new StringBuilder();
                            for (DocumentTable dt : tables) {
                                tableText.append("[TABLE] ")
                                        .append(dt.caption() != null ? dt.caption() : "Table")
                                        .append(" (").append(dt.rowCount()).append("x").append(dt.colCount()).append(")\n");
                                for (int r = 0; r < dt.rows().size(); r++) {
                                    tableText.append("| ").append(String.join(" | ", dt.rows().get(r))).append(" |\n");
                                }
                                tableText.append("\n");
                            }
                            DocumentChunk chunk = new DocumentChunk(
                                    sourcePath + "#t" + offset, pageNo,
                                    currentSection, null, tableText.toString().trim(),
                                    tables, null, offset, sourcePath, "docx"
                            );
                            chunks.add(chunk);
                        }
                        offset++;
                    }
                }
            }
        }

        return new DocumentParseResult(chunks, 1, false, metadata);
    }

    private int extractHeadingLevel(String style) {
        for (int i = 9; i >= 1; i--) {
            if (style.toLowerCase().contains("heading" + i) || style.toLowerCase().contains("heading " + i)) {
                return i;
            }
        }
        return 1;
    }

    private List<DocumentTable> extractTables(XWPFTable table, int pageNo, String section, String sourcePath) {
        List<DocumentTable> result = new ArrayList<>();
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();

        List<XWPFTableRow> tableRows = table.getRows();
        if (tableRows.isEmpty()) return result;

        for (int i = 0; i < tableRows.size(); i++) {
            List<String> row = new ArrayList<>();
            for (XWPFTableCell cell : tableRows.get(i).getTableCells()) {
                row.add(cell.getText().trim());
            }
            if (i == 0) {
                headers = row;
            } else {
                rows.add(row);
            }
        }

        result.add(new DocumentTable(
                pageNo, section, null,
                headers, rows,
                rows.size(), headers.size()
        ));
        return result;
    }
}