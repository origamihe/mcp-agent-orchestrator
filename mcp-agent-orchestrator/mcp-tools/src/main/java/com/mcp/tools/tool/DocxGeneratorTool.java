package com.mcp.tools.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.tools.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocxGeneratorTool {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ContentValidator contentValidator;

    @Value("${docx.output.dir:./generated/docx}")
    private String outputDir;

    public record DocxResult(String fileName, String downloadUrl, String mimeType,
                             long size, String message) {}

    /**
     * 三步流程：解析 → 校验 → 渲染
     */
    public DocxResult generateDocx(String llmResponse, String userTitle) throws Exception {
        Path dir = Path.of(outputDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        DocumentModel model = parseToModel(llmResponse, userTitle);

        ValidationResult validation = contentValidator.validateDocx(model);
        if (!validation.valid()) {
            log.warn("DOCX validation errors: {}", validation.errors());
            throw new IllegalArgumentException("文档内容校验失败: " + String.join("; ", validation.errors()));
        }
        if (!validation.warnings().isEmpty()) {
            log.info("DOCX validation warnings: {}", validation.warnings());
        }

        DocumentTheme.DocxThemeConfig theme = (DocumentTheme.DocxThemeConfig)
                DocumentTheme.of(model.theme());

        String fileName = buildFileName(model.title());
        Path outputPath = dir.resolve(fileName);

        int blockCount = renderDocx(model, theme, outputPath);

        long fileSize = Files.size(outputPath);
        String downloadUrl = "/mcp/download/docx/" + fileName;
        String message = String.format("Word 文档已生成！标题：%s，%d 个章节，%d 个内容块。",
                model.title(), model.sections().size(), blockCount);

        log.info("DOCX generated: {} ({} sections, {} blocks, {} bytes)",
                outputPath, model.sections().size(), blockCount, fileSize);
        return new DocxResult(fileName, downloadUrl,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                fileSize, message);
    }

    // ===== 第一步：解析 =====

    private DocumentModel parseToModel(String llmResponse, String userTitle) {
        try {
            String json = extractJson(llmResponse);
            return objectMapper.readValue(json, DocumentModel.class);
        } catch (Exception e) {
            log.warn("Failed to parse DocumentModel from LLM response, using fallback: {}", e.getMessage());
            return buildFallbackModel(llmResponse, userTitle);
        }
    }

    // ===== 第二步：校验（委托给 ContentValidator） =====

    // ===== 第三步：渲染 =====

    private int renderDocx(DocumentModel model, DocumentTheme.DocxThemeConfig theme,
                           Path outputPath) throws Exception {
        int blockCount = 0;

        try (XWPFDocument doc = new XWPFDocument()) {
            renderTitle(doc, model.title(), theme);

            for (DocumentModel.Section section : model.sections()) {
                renderSectionHeading(doc, section, theme);
                for (Block block : section.blocks()) {
                    renderBlock(doc, block, theme);
                    blockCount++;
                }
                addSpacer(doc);
            }

            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                doc.write(fos);
            }
        }

        return blockCount;
    }

    private void renderTitle(XWPFDocument doc, String title, DocumentTheme.DocxThemeConfig theme) {
        XWPFParagraph para = doc.createParagraph();
        para.setAlignment(ParagraphAlignment.CENTER);
        para.setSpacingBefore(theme.spaceBefore() * 2);
        para.setSpacingAfter(theme.spaceAfter() * 4);
        XWPFRun run = para.createRun();
        run.setText(title);
        run.setFontSize(theme.titleFontSize());
        run.setBold(true);
        run.setFontFamily(theme.fontFamily());
        run.setColor(hex(theme.titleColor()));
    }

    private void renderSectionHeading(XWPFDocument doc, DocumentModel.Section section,
                                      DocumentTheme.DocxThemeConfig theme) {
        if (section.title() == null || section.title().isBlank()) return;

        int fontSize = switch (section.level()) {
            case 1 -> theme.h1FontSize();
            case 2 -> theme.h2FontSize();
            default -> theme.h3FontSize();
        };

        XWPFParagraph para = doc.createParagraph();
        para.setSpacingBefore(theme.spaceBefore());
        para.setSpacingAfter(theme.spaceAfter());
        XWPFRun run = para.createRun();
        run.setText(section.title());
        run.setFontSize(fontSize);
        run.setBold(true);
        run.setFontFamily(theme.fontFamily());
        run.setColor(hex(theme.headingColor()));
    }

    private void renderBlock(XWPFDocument doc, Block block, DocumentTheme.DocxThemeConfig theme) {
        switch (block) {
            case Block.Paragraph p -> renderParagraph(doc, p.text(), theme);
            case Block.BulletList bl -> renderBulletList(doc, bl.items(), theme);
            case Block.OrderedList ol -> renderOrderedList(doc, ol.items(), theme);
            case Block.Table t -> renderTable(doc, t, theme);
            case Block.Quote q -> renderQuote(doc, q, theme);
            case Block.CodeBlock cb -> renderCodeBlock(doc, cb, theme);
            case Block.Heading h -> renderInlineHeading(doc, h, theme);
            default -> log.debug("Unsupported block type: {}", block.getClass().getSimpleName());
        }
    }

    private void renderParagraph(XWPFDocument doc, String text, DocumentTheme.DocxThemeConfig theme) {
        XWPFParagraph para = doc.createParagraph();
        para.setFirstLineIndent(theme.firstLineIndent());
        para.setSpacingBetween(theme.lineSpacing());
        para.setSpacingBefore(theme.spaceBefore());
        para.setSpacingAfter(theme.spaceAfter());
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setFontSize(theme.bodyFontSize());
        run.setFontFamily(theme.fontFamily());
        run.setColor(hex(theme.bodyColor()));
    }

    private void renderBulletList(XWPFDocument doc, java.util.List<String> items,
                                  DocumentTheme.DocxThemeConfig theme) {
        for (String item : items) {
            XWPFParagraph para = doc.createParagraph();
            para.setIndentationLeft(600);
            para.setSpacingBetween(theme.lineSpacing());
            para.setSpacingBefore(theme.spaceBefore() / 2);
            para.setSpacingAfter(theme.spaceAfter() / 2);

            CTPPr pPr = para.getCTP().isSetPPr() ? para.getCTP().getPPr() : para.getCTP().addNewPPr();
            CTString pStyle = pPr.addNewPStyle();
            pStyle.setVal("ListBullet");

            XWPFRun run = para.createRun();
            run.setText("• " + item);
            run.setFontSize(theme.bodyFontSize());
            run.setFontFamily(theme.fontFamily());
            run.setColor(hex(theme.bodyColor()));
        }
    }

    private void renderOrderedList(XWPFDocument doc, java.util.List<String> items,
                                   DocumentTheme.DocxThemeConfig theme) {
        for (int i = 0; i < items.size(); i++) {
            XWPFParagraph para = doc.createParagraph();
            para.setIndentationLeft(600);
            para.setSpacingBetween(theme.lineSpacing());
            para.setSpacingBefore(theme.spaceBefore() / 2);
            para.setSpacingAfter(theme.spaceAfter() / 2);
            XWPFRun run = para.createRun();
            run.setText((i + 1) + ". " + items.get(i));
            run.setFontSize(theme.bodyFontSize());
            run.setFontFamily(theme.fontFamily());
            run.setColor(hex(theme.bodyColor()));
        }
    }

    private void renderTable(XWPFDocument doc, Block.Table table, DocumentTheme.DocxThemeConfig theme) {
        int rows = table.rows().size() + 1;
        int cols = table.headers().size();
        XWPFTable tbl = doc.createTable(rows, cols);
        tbl.setWidth("100%");

        for (int c = 0; c < cols; c++) {
            XWPFTableCell cell = tbl.getRow(0).getCell(c);
            setCellText(cell, table.headers().get(c), theme, true);
        }

        for (int r = 0; r < table.rows().size(); r++) {
            for (int c = 0; c < cols && c < table.rows().get(r).size(); c++) {
                XWPFTableCell cell = tbl.getRow(r + 1).getCell(c);
                setCellText(cell, table.rows().get(r).get(c), theme, false);
            }
        }

        addSpacer(doc);
    }

    private void setCellText(XWPFTableCell cell, String text,
                             DocumentTheme.DocxThemeConfig theme, boolean isHeader) {
        cell.removeParagraph(0);
        XWPFParagraph para = cell.addParagraph();
        para.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setFontSize(isHeader ? theme.bodyFontSize() : theme.bodyFontSize() - 1);
        run.setBold(isHeader);
        run.setFontFamily(theme.fontFamily());
        run.setColor(hex(theme.bodyColor()));
    }

    private void renderQuote(XWPFDocument doc, Block.Quote quote, DocumentTheme.DocxThemeConfig theme) {
        XWPFParagraph para = doc.createParagraph();
        para.setIndentationLeft(800);
        para.setIndentationRight(400);
        para.setSpacingBefore(theme.spaceBefore());
        para.setSpacingAfter(theme.spaceAfter());

        CTShd shd = para.getCTP().addNewPPr().addNewShd();
        shd.setFill("F0F0F0");

        XWPFRun run = para.createRun();
        run.setItalic(true);
        run.setText(quote.text());
        run.setFontSize(theme.bodyFontSize());
        run.setFontFamily(theme.fontFamily());
        run.setColor(hex(theme.bodyColor()));

        if (quote.attribution() != null && !quote.attribution().isBlank()) {
            run.addBreak();
            XWPFRun attrRun = para.createRun();
            attrRun.setText("— " + quote.attribution());
            attrRun.setFontSize(theme.bodyFontSize() - 2);
            attrRun.setFontFamily(theme.fontFamily());
            attrRun.setColor(hex(theme.bodyColor()));
        }
    }

    private void renderCodeBlock(XWPFDocument doc, Block.CodeBlock codeBlock,
                                 DocumentTheme.DocxThemeConfig theme) {
        XWPFParagraph para = doc.createParagraph();
        para.setIndentationLeft(400);
        para.setSpacingBefore(theme.spaceBefore());
        para.setSpacingAfter(theme.spaceAfter());

        CTShd shd = para.getCTP().addNewPPr().addNewShd();
        shd.setFill("1E1E1E");

        XWPFRun run = para.createRun();
        run.setText(codeBlock.code());
        run.setFontSize(theme.bodyFontSize() - 2);
        run.setFontFamily("Consolas");
        run.setColor("D4D4D4");
    }

    private void renderInlineHeading(XWPFDocument doc, Block.Heading heading,
                                     DocumentTheme.DocxThemeConfig theme) {
        int fontSize = switch (heading.level()) {
            case 1 -> theme.h1FontSize();
            case 2 -> theme.h2FontSize();
            default -> theme.h3FontSize();
        };
        XWPFParagraph para = doc.createParagraph();
        para.setSpacingBefore(theme.spaceBefore());
        para.setSpacingAfter(theme.spaceAfter());
        XWPFRun run = para.createRun();
        run.setText(heading.text());
        run.setFontSize(fontSize);
        run.setBold(true);
        run.setFontFamily(theme.fontFamily());
        run.setColor(hex(theme.headingColor()));
    }

    private void addSpacer(XWPFDocument doc) {
        XWPFParagraph para = doc.createParagraph();
        para.setSpacingAfter(0);
        para.setSpacingBefore(0);
        para.createRun().setText("");
    }

    private static String hex(java.awt.Color color) {
        return String.format("%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
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

    /**
     * 升级版 fallback：识别 Markdown 标题层级、有序/无序列表、表格、代码块，
     * 返回结构化的 DocumentModel 而非裸 JsonNode
     */
    private DocumentModel buildFallbackModel(String response, String userTitle) {
        String[] lines = response.split("\n");
        var sections = new java.util.ArrayList<DocumentModel.Section>();
        var currentBlocks = new java.util.ArrayList<Block>();
        String currentSectionTitle = userTitle;
        int currentLevel = 1;
        boolean hasAnySection = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            if (line.isEmpty()) {
                if (!currentBlocks.isEmpty()) {
                    currentBlocks.add(new Block.Paragraph(""));
                }
                continue;
            }

            if (line.startsWith("#")) {
                if (hasAnySection || !currentBlocks.isEmpty()) {
                    sections.add(new DocumentModel.Section(
                            currentSectionTitle, currentLevel, List.copyOf(currentBlocks)));
                    currentBlocks = new java.util.ArrayList<>();
                }
                hasAnySection = true;
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') level++;
                currentSectionTitle = line.substring(level).trim();
                currentLevel = Math.min(level, 3);
            } else if (line.matches("^\\d+\\.\\s.*")) {
                var items = new java.util.ArrayList<String>();
                items.add(line.replaceFirst("^\\d+\\.\\s*", ""));
                while (i + 1 < lines.length && lines[i + 1].trim().matches("^\\d+\\.\\s.*")) {
                    i++;
                    items.add(lines[i].trim().replaceFirst("^\\d+\\.\\s*", ""));
                }
                currentBlocks.add(new Block.OrderedList(items));
            } else if (line.startsWith("-") || line.startsWith("*")) {
                var items = new java.util.ArrayList<String>();
                items.add(line.replaceFirst("^[-*]\\s*", ""));
                while (i + 1 < lines.length &&
                        (lines[i + 1].trim().startsWith("-") || lines[i + 1].trim().startsWith("*"))) {
                    i++;
                    items.add(lines[i].trim().replaceFirst("^[-*]\\s*", ""));
                }
                currentBlocks.add(new Block.BulletList(items));
            } else if (line.startsWith(">")) {
                var quoteLines = new StringBuilder();
                quoteLines.append(line.replaceFirst("^>\\s*", ""));
                while (i + 1 < lines.length && lines[i + 1].trim().startsWith(">")) {
                    i++;
                    quoteLines.append(" ").append(lines[i].trim().replaceFirst("^>\\s*", ""));
                }
                currentBlocks.add(new Block.Quote(quoteLines.toString(), null));
            } else if (line.startsWith("```")) {
                var codeLines = new StringBuilder();
                String lang = line.substring(3).trim();
                while (i + 1 < lines.length && !lines[i + 1].trim().startsWith("```")) {
                    i++;
                    codeLines.append(lines[i]).append("\n");
                }
                i++;
                currentBlocks.add(new Block.CodeBlock(lang.isEmpty() ? "text" : lang,
                        codeLines.toString().trim()));
            } else {
                currentBlocks.add(new Block.Paragraph(line));
            }
        }

        if (!currentBlocks.isEmpty() || !hasAnySection) {
            sections.add(new DocumentModel.Section(
                    currentSectionTitle, currentLevel, List.copyOf(currentBlocks)));
        }

        return new DocumentModel(userTitle, "docx", "business",
                sections.isEmpty()
                        ? java.util.List.of(new DocumentModel.Section(
                                userTitle, 1, java.util.List.of(new Block.Paragraph(response))))
                        : sections);
    }

    private String buildFileName(String title) {
        String safeName = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (safeName.length() > 40) safeName = safeName.substring(0, 40);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String shortId = UUID.randomUUID().toString().substring(0, 6);
        return safeName + "_" + timestamp + "_" + shortId + ".docx";
    }
}