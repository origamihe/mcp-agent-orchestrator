package com.mcp.tools.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class PptGeneratorTool {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ppt.output.dir:./generated/ppt}")
    private String outputDir;

    public record PptResult(String downloadUrl, String message, String fileName) {}

    public PptResult generatePptx(String llmResponse, String userTitle) throws Exception {
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
        String fileName = safeName + "_" + timestamp + ".pptx";
        Path outputPath = dir.resolve(fileName);

        try (XMLSlideShow ppt = new XMLSlideShow()) {
            ppt.setPageSize(new java.awt.Dimension(960, 540));

            XSLFSlideMaster defaultMaster = ppt.getSlideMasters().get(0);
            XSLFSlideLayout layout = defaultMaster.getLayout(SlideLayout.TITLE_AND_CONTENT);

            JsonNode slides = root.get("slides");
            if (slides != null && slides.isArray()) {
                boolean firstSlide = true;
                for (JsonNode slideNode : slides) {
                    String slideTitle = slideNode.has("title") ? slideNode.get("title").asText() : "";
                    XSLFSlide slide;

                    if (firstSlide) {
                        XSLFSlideLayout titleLayout = defaultMaster.getLayout(SlideLayout.TITLE);
                        slide = ppt.createSlide(titleLayout);
                        firstSlide = false;

                        XSLFTextShape mainTitle = slide.getPlaceholder(0);
                        if (mainTitle != null) {
                            mainTitle.clearText();
                            XSLFTextParagraph p = mainTitle.addNewTextParagraph();
                            p.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                            XSLFTextRun r = p.addNewTextRun();
                            r.setText(title);
                            r.setFontSize(36.0);
                            r.setBold(true);
                            r.setFontColor(new Color(0x1A, 0x1A, 0x2E));
                            r.setFontFamily("Microsoft YaHei");
                        }

                        XSLFTextShape subtitle = slide.getPlaceholder(1);
                        if (subtitle != null) {
                            subtitle.clearText();
                            XSLFTextParagraph sp = subtitle.addNewTextParagraph();
                            sp.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                            XSLFTextRun sr = sp.addNewTextRun();
                            sr.setText(slideTitle);
                            sr.setFontSize(20.0);
                            sr.setFontColor(new Color(0x66, 0x7E, 0xEA));
                            sr.setFontFamily("Microsoft YaHei");
                        }
                        continue;
                    }

                    slide = ppt.createSlide(layout);

                    XSLFTextShape titleShape = slide.getPlaceholder(0);
                    if (titleShape != null && !slideTitle.isEmpty()) {
                        titleShape.clearText();
                        XSLFTextParagraph tp = titleShape.addNewTextParagraph();
                        XSLFTextRun tr = tp.addNewTextRun();
                        tr.setText(slideTitle);
                        tr.setFontSize(28.0);
                        tr.setBold(true);
                        tr.setFontColor(new Color(0x1A, 0x1A, 0x2E));
                        tr.setFontFamily("Microsoft YaHei");
                    }

                    XSLFTextShape bodyShape = slide.getPlaceholder(1);
                    if (bodyShape != null) {
                        bodyShape.clearText();
                        JsonNode content = slideNode.get("content");
                        if (content != null && content.isArray()) {
                            for (int i = 0; i < content.size(); i++) {
                                String point = content.get(i).asText();
                                XSLFTextParagraph bp = bodyShape.addNewTextParagraph();
                                bp.setBullet(true);
                                bp.setIndentLevel(1);
                                XSLFTextRun br = bp.addNewTextRun();
                                br.setText(point);
                                br.setFontSize(18.0);
                                br.setFontColor(new Color(0x33, 0x33, 0x33));
                                br.setFontFamily("Microsoft YaHei");
                            }
                        }
                    }
                }
            }

            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                ppt.write(fos);
            }
        }

        String downloadUrl = "/mcp/download/ppt/" + fileName;
        String message = "PPT 已生成成功！主题：" + title + "，共 " +
                (root.has("slides") ? root.get("slides").size() : 0) + " 页幻灯片。";

        log.info("PPT generated: {} ({} slides)", outputPath, root.has("slides") ? root.get("slides").size() : 0);
        return new PptResult(downloadUrl, message, fileName);
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
        var slides = objectMapper.createArrayNode();
        var currentSlide = objectMapper.createObjectNode();
        var content = objectMapper.createArrayNode();

        int slideNum = 0;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#") || line.startsWith("##") || line.startsWith("###")) {
                if (currentSlide.has("title")) {
                    currentSlide.set("content", content);
                    slides.add(currentSlide);
                    content = objectMapper.createArrayNode();
                }
                slideNum++;
                currentSlide = objectMapper.createObjectNode();
                currentSlide.put("title", line.replaceFirst("^#+\\s*", ""));
            } else if (line.startsWith("-") || line.startsWith("*") || slideNum == 0) {
                if (slideNum == 0) {
                    slideNum = 1;
                    currentSlide = objectMapper.createObjectNode();
                    currentSlide.put("title", userTitle);
                }
                content.add(line.replaceFirst("^[-*]\\s*", ""));
            }
        }
        if (currentSlide.has("title")) {
            currentSlide.set("content", content);
            slides.add(currentSlide);
        }

        node.set("slides", slides);
        return node;
    }
}