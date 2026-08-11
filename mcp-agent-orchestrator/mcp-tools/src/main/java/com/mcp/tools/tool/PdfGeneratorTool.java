package com.mcp.tools.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PDF 生成器 — 基于 Apache PDFBox 生成 PDF 文档。
 *
 * 输入格式（LLM JSON）：
 * {
 *   "title": "文档标题",
 *   "sections": [
 *     {"title": "第一节", "content": ["段落1", "段落2"]}
 *   ]
 * }
 */
@Slf4j
@Component
public class PdfGeneratorTool implements DocumentGenerator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${pdf.output.dir:./generated/pdf}")
    private String outputDir;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final float MARGIN = 50;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    private static final float TITLE_FONT_SIZE = 18;
    private static final float HEADING_FONT_SIZE = 14;
    private static final float BODY_FONT_SIZE = 11;
    private static final float LINE_SPACING = 1.5f;

    @PostConstruct
    public void init() {
        log.info("[PdfGen] Output directory: {}", outputDir);
    }

    @Override
    public DocResult generate(String llmResponse, String userTitle) throws Exception {
        Path dir = Path.of(outputDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        JsonNode root = objectMapper.readTree(llmResponse);
        String title = root.has("title") ? root.get("title").asText() : userTitle;
        JsonNode sections = root.has("sections") ? root.get("sections") : null;

        String fileName = sanitizeFileName(title) + "_" + TS_FMT.format(LocalDateTime.now()) + ".pdf";
        Path filePath = dir.resolve(fileName);

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDPageContentStream content = new PDPageContentStream(document, page);
            float y = PAGE_HEIGHT - MARGIN;

            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), TITLE_FONT_SIZE);
            y = writeLine(content, title, MARGIN, y, TITLE_FONT_SIZE);
            y -= 10;

            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), BODY_FONT_SIZE);

            if (sections != null && sections.isArray()) {
                for (JsonNode section : sections) {
                    if (y < MARGIN + 50) {
                        content.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        content = new PDPageContentStream(document, page);
                        y = PAGE_HEIGHT - MARGIN;
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), BODY_FONT_SIZE);
                    }

                    if (section.has("title")) {
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), HEADING_FONT_SIZE);
                        y = writeLine(content, section.get("title").asText(), MARGIN, y, HEADING_FONT_SIZE);
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), BODY_FONT_SIZE);
                        y -= 4;
                    }

                    JsonNode contentItems = section.get("content");
                    if (contentItems != null && contentItems.isArray()) {
                        for (JsonNode item : contentItems) {
                            List<String> lines = wrapText(item.asText(), CONTENT_WIDTH, BODY_FONT_SIZE);
                            for (String line : lines) {
                                if (y < MARGIN) {
                                    content.close();
                                    page = new PDPage(PDRectangle.A4);
                                    document.addPage(page);
                                    content = new PDPageContentStream(document, page);
                                    y = PAGE_HEIGHT - MARGIN;
                                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), BODY_FONT_SIZE);
                                }
                                y = writeLine(content, line, MARGIN, y, BODY_FONT_SIZE);
                            }
                            y -= 4;
                        }
                    }
                }
            } else {
                List<String> lines = wrapText(llmResponse, CONTENT_WIDTH, BODY_FONT_SIZE);
                for (String line : lines) {
                    if (y < MARGIN) {
                        content.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        content = new PDPageContentStream(document, page);
                        y = PAGE_HEIGHT - MARGIN;
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), BODY_FONT_SIZE);
                    }
                    y = writeLine(content, line, MARGIN, y, BODY_FONT_SIZE);
                }
            }

            content.close();
            document.save(filePath.toFile());
        }

        long size = Files.size(filePath);
        log.info("[PdfGen] Generated: {} ({} bytes)", fileName, size);
        return new DocResult(fileName, "/files/pdf/" + fileName,
                "application/pdf", size, "PDF 生成成功: " + fileName);
    }

    @Override
    public DocResult generateFromMarkdown(String markdownContent, String title) throws Exception {
        return generate("{\"title\":\"" + escapeJson(title) + "\",\"sections\":[{\"content\":[\"" +
                escapeJson(markdownContent) + "\"]}]}", title);
    }

    @Override
    public boolean supports(String format) {
        return "pdf".equalsIgnoreCase(format);
    }

    private float writeLine(PDPageContentStream content, String text, float x, float y, float fontSize) throws Exception {
        content.beginText();
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
        return y - fontSize * LINE_SPACING;
    }

    private List<String> wrapText(String text, float maxWidth, float fontSize) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return lines;

        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String testLine = line.length() > 0 ? line + " " + word : word;
            float width = getStringWidth(testLine, fontSize);
            if (width > maxWidth && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                if (line.length() > 0) line.append(" ");
                line.append(word);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines.isEmpty() ? List.of(text) : lines;
    }

    private float getStringWidth(String text, float fontSize) {
        return text.length() * fontSize * 0.5f;
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) return "document";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").substring(0, Math.min(name.length(), 50));
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}