package com.mcp.tools.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class DocxGeneratorTool {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${docx.output.dir:./generated/docx}")
    private String outputDir;

    public record DocxResult(String downloadUrl, String message, String fileName) {}

    public DocxResult generateDocx(String llmResponse, String userTitle) throws Exception {
        Path dir = Path.of(outputDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        String title = userTitle;
        JsonNode root;

        try {
            root = objectMapper.readTree(extractJson(llmResponse));
        } catch (Exception e) {
            log.warn("Failed to parse JSON from LLM response, using fallback parsing: {}", e.getMessage());
            root = buildFallbackJson(llmResponse, userTitle);
        }

        if (root.has("title")) {
            title = root.get("title").asText();
        }

        String safeName = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (safeName.length() > 50) safeName = safeName.substring(0, 50);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String fileName = safeName + "_" + timestamp + ".docx";
        Path outputPath = dir.resolve(fileName);

        int sectionCount = 0;

        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph titlePara = doc.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText(title);
            titleRun.setFontSize(24);
            titleRun.setBold(true);
            titleRun.setFontFamily("Microsoft YaHei");
            titleRun.addBreak();
            titleRun.addBreak();

            JsonNode sections = root.get("sections");
            if (sections != null && sections.isArray()) {
                for (JsonNode sectionNode : sections) {
                    String sectionTitle = sectionNode.has("title") ? sectionNode.get("title").asText() : "";
                    JsonNode content = sectionNode.get("content");

                    if (!sectionTitle.isEmpty()) {
                        XWPFParagraph headingPara = doc.createParagraph();
                        XWPFRun headingRun = headingPara.createRun();
                        headingRun.setText(sectionTitle);
                        headingRun.setFontSize(18);
                        headingRun.setBold(true);
                        headingRun.setFontFamily("Microsoft YaHei");
                        headingRun.addBreak();
                    }

                    if (content != null && content.isArray()) {
                        for (int i = 0; i < content.size(); i++) {
                            String paragraph = content.get(i).asText();
                            XWPFParagraph contentPara = doc.createParagraph();
                            contentPara.setFirstLineIndent(400);
                            XWPFRun contentRun = contentPara.createRun();
                            contentRun.setText(paragraph);
                            contentRun.setFontSize(12);
                            contentRun.setFontFamily("Microsoft YaHei");
                        }
                        sectionCount++;
                    } else if (content != null && content.isTextual()) {
                        XWPFParagraph contentPara = doc.createParagraph();
                        contentPara.setFirstLineIndent(400);
                        XWPFRun contentRun = contentPara.createRun();
                        contentRun.setText(content.asText());
                        contentRun.setFontSize(12);
                        contentRun.setFontFamily("Microsoft YaHei");
                        sectionCount++;
                    }

                    XWPFParagraph spacePara = doc.createParagraph();
                    XWPFRun spaceRun = spacePara.createRun();
                    spaceRun.setText("");
                }
            }

            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                doc.write(fos);
            }
        }

        String downloadUrl = "/mcp/download/docx/" + fileName;
        String message = "Word 文档已生成成功！标题：" + title + "，共 " + sectionCount + " 个章节。";

        log.info("DOCX generated: {} ({} sections)", outputPath, sectionCount);
        return new DocxResult(downloadUrl, message, fileName);
    }

    private String extractJson(String response) {
        String cleaned = response
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .replace("\uFEFF", "")
                .trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return cleaned;
    }

    private JsonNode buildFallbackJson(String response, String userTitle) {
        String[] lines = response.split("\n");
        var node = objectMapper.createObjectNode();
        node.put("title", userTitle);
        var sections = objectMapper.createArrayNode();
        var currentSection = objectMapper.createObjectNode();
        var content = objectMapper.createArrayNode();

        int sectionNum = 0;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#") || line.startsWith("##") || line.startsWith("###")) {
                if (currentSection.has("title")) {
                    currentSection.set("content", content);
                    sections.add(currentSection);
                    content = objectMapper.createArrayNode();
                }
                sectionNum++;
                currentSection = objectMapper.createObjectNode();
                currentSection.put("title", line.replaceFirst("^#+\\s*", ""));
            } else if (line.startsWith("-") || line.startsWith("*") || sectionNum == 0) {
                if (sectionNum == 0) {
                    sectionNum = 1;
                    currentSection = objectMapper.createObjectNode();
                    currentSection.put("title", userTitle);
                }
                content.add(line.replaceFirst("^[-*]\\s*", ""));
            } else {
                content.add(line);
            }
        }
        if (currentSection.has("title")) {
            currentSection.set("content", content);
            sections.add(currentSection);
        }

        node.set("sections", sections);
        return node;
    }
}