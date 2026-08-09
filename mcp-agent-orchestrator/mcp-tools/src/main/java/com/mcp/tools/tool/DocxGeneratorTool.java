package com.mcp.tools.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.tools.annotation.McpTool;
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
    @McpTool(
            name = "generate_docx",
            description = "生成Word文档(.docx)。参数llmResponse为LLM生成的JSON格式文档内容，userTitle为文档标题。"
                    + "调用前需先由LLM生成结构化JSON内容，格式为{\"title\":\"...\",\"sections\":[{\"title\":\"...\",\"content\":[\"...\"]}]}",
            tags = {"document", "docx", "generate", "file"},
            category = ToolCategory.DOCUMENT,
            capabilities = {ToolCapability.CUSTOM},
            timeoutMs = 60000
    )
    public DocxResult generateDocx(String llmResponse, String userTitle) throws Exception {
        Path dir = Path.of(outputDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        DocumentModel model = parseToModel(llmResponse, userTitle);

        return renderAndSave(model, dir);
    }

    /**
     * 从 Markdown 文本直接生成 DOCX（纯渲染器模式，不调用 LLM）。
     * 这是推荐的文档生成路径：SearchAgent → Markdown → DocxTool → DOCX
     */
    public DocxResult generateDocxFromMarkdown(String markdown, String title) throws Exception {
        Path dir = Path.of(outputDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        DocumentModel model = parseMarkdownToModel(markdown, title);

        return renderAndSave(model, dir);
    }

    private DocxResult renderAndSave(DocumentModel model, Path dir) throws Exception {
        ValidationResult validation = contentValidator.validateDocx(model);
        if (!validation.valid()) {
            // P2-1 改进：区分致命错误和警告。只有标题/章节缺失才是致命错误
            boolean hasFatalErrors = validation.errors().stream()
                    .anyMatch(e -> e.contains("标题不能为空") || e.contains("至少需要一个章节"));
            if (hasFatalErrors) {
                log.error("DOCX fatal validation errors: {}", validation.errors());
                throw new IllegalArgumentException("文档内容校验失败: " + String.join("; ", validation.errors()));
            }
            // 非致命错误降级为警告
            log.warn("DOCX validation errors (non-fatal, continuing): {}", validation.errors());
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
            log.error("Failed to parse DocumentModel from LLM response: {}", e.getMessage());
            throw new IllegalArgumentException("文档内容解析失败，无法生成文档: " + e.getMessage(), e);
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
     * Markdown 解析器 — 将 Markdown 文本转换为结构化 DocumentModel。
     * P2-2 改进：支持更多 Markdown 特性（表格、粗体/斜体、分隔线、多行段落聚合）。
     */
    private DocumentModel parseMarkdownToModel(String markdown, String title) {
        String[] lines = markdown.split("\n");
        var sections = new java.util.ArrayList<DocumentModel.Section>();
        var currentBlocks = new java.util.ArrayList<Block>();
        String currentSectionTitle = title;
        int currentLevel = 1;
        boolean hasAnySection = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // 跳过空行
            if (line.isEmpty()) {
                continue;
            }

            // 分隔线
            if (line.matches("^[-*_]{3,}$")) {
                // 水平分隔线：作为空段落占位
                currentBlocks.add(new Block.Paragraph(""));
                continue;
            }

            // 标题
            if (line.startsWith("#")) {
                if (hasAnySection || !currentBlocks.isEmpty()) {
                    sections.add(new DocumentModel.Section(
                            currentSectionTitle, currentLevel, List.copyOf(currentBlocks)));
                    currentBlocks = new java.util.ArrayList<>();
                }
                hasAnySection = true;
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') level++;
                currentSectionTitle = stripMarkdownInline(line.substring(level).trim());
                currentLevel = Math.min(level, 3);
                continue;
            }

            // 表格：| col1 | col2 | col3 |
            if (line.startsWith("|") && line.endsWith("|")) {
                // 跳过表头分隔行（|---|---|）
                if (line.matches("^\\|[\\s\\-:|]+\\|$")) {
                    continue;
                }
                var headers = new java.util.ArrayList<String>();
                var rows = new java.util.ArrayList<java.util.List<String>>();
                String[] cells = line.split("\\|");
                for (String cell : cells) {
                    String trimmed = cell.trim();
                    if (!trimmed.isEmpty()) {
                        headers.add(stripMarkdownInline(trimmed));
                    }
                }
                // 收集后续表格行
                while (i + 1 < lines.length && lines[i + 1].trim().startsWith("|")) {
                    i++;
                    String rowLine = lines[i].trim();
                    if (rowLine.matches("^\\|[\\s\\-:|]+\\|$")) continue;
                    var row = new java.util.ArrayList<String>();
                    for (String cell : rowLine.split("\\|")) {
                        String trimmed = cell.trim();
                        if (!trimmed.isEmpty()) {
                            row.add(stripMarkdownInline(trimmed));
                        }
                    }
                    if (!row.isEmpty()) rows.add(row);
                }
                if (!headers.isEmpty()) {
                    currentBlocks.add(new Block.Table(headers, rows));
                }
                continue;
            }

            // 有序列表
            if (line.matches("^\\d+\\.\\s.*")) {
                var items = new java.util.ArrayList<String>();
                items.add(stripMarkdownInline(line.replaceFirst("^\\d+\\.\\s*", "")));
                while (i + 1 < lines.length && lines[i + 1].trim().matches("^\\d+\\.\\s.*")) {
                    i++;
                    items.add(stripMarkdownInline(lines[i].trim().replaceFirst("^\\d+\\.\\s*", "")));
                }
                currentBlocks.add(new Block.OrderedList(items));
                continue;
            }

            // 无序列表（支持多级缩进：- 和 * 开头，以及缩进的子列表）
            if (line.startsWith("-") || line.startsWith("*")) {
                var items = new java.util.ArrayList<String>();
                items.add(stripMarkdownInline(line.replaceFirst("^[-*]\\s*", "")));
                while (i + 1 < lines.length &&
                        (lines[i + 1].trim().startsWith("-") || lines[i + 1].trim().startsWith("*"))) {
                    i++;
                    items.add(stripMarkdownInline(lines[i].trim().replaceFirst("^[-*]\\s*", "")));
                }
                currentBlocks.add(new Block.BulletList(items));
                continue;
            }

            // 引用块
            if (line.startsWith(">")) {
                var quoteLines = new StringBuilder();
                quoteLines.append(stripMarkdownInline(line.replaceFirst("^>\\s*", "")));
                while (i + 1 < lines.length && lines[i + 1].trim().startsWith(">")) {
                    i++;
                    quoteLines.append(" ").append(stripMarkdownInline(lines[i].trim().replaceFirst("^>\\s*", "")));
                }
                currentBlocks.add(new Block.Quote(quoteLines.toString(), null));
                continue;
            }

            // 代码块
            if (line.startsWith("```")) {
                var codeLines = new StringBuilder();
                String lang = line.substring(3).trim();
                while (i + 1 < lines.length && !lines[i + 1].trim().startsWith("```")) {
                    i++;
                    codeLines.append(lines[i]).append("\n");
                }
                i++; // 跳过闭合的 ```
                currentBlocks.add(new Block.CodeBlock(lang.isEmpty() ? "text" : lang,
                        codeLines.toString().trim()));
                continue;
            }

            // 普通段落：聚合连续的非空行
            var paraLines = new StringBuilder();
            paraLines.append(stripMarkdownInline(line));
            while (i + 1 < lines.length) {
                String nextLine = lines[i + 1].trim();
                if (nextLine.isEmpty() || nextLine.startsWith("#") || nextLine.startsWith("```")
                        || nextLine.startsWith("|") || nextLine.startsWith(">")
                        || nextLine.startsWith("-") || nextLine.startsWith("*")
                        || nextLine.matches("^\\d+\\.\\s.*") || nextLine.matches("^[-*_]{3,}$")) {
                    break;
                }
                i++;
                paraLines.append(" ").append(stripMarkdownInline(nextLine));
            }
            currentBlocks.add(new Block.Paragraph(paraLines.toString()));
        }

        if (!currentBlocks.isEmpty() || !hasAnySection) {
            sections.add(new DocumentModel.Section(
                    currentSectionTitle, currentLevel, List.copyOf(currentBlocks)));
        }

        return new DocumentModel(title, "docx", "business",
                sections.isEmpty()
                        ? java.util.List.of(new DocumentModel.Section(
                                title, 1, java.util.List.of(new Block.Paragraph(markdown))))
                        : sections);
    }

    /**
     * 移除 Markdown 内联格式标记（粗体、斜体、行内代码、链接等），保留纯文本。
     */
    private String stripMarkdownInline(String text) {
        if (text == null || text.isEmpty()) return text;
        return text
                // 粗体 **text** 或 __text__
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("__(.+?)__", "$1")
                // 斜体 *text* 或 _text_
                .replaceAll("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", "$1")
                .replaceAll("(?<!_)_(?!_)(.+?)(?<!_)_(?!_)", "$1")
                // 行内代码 `code`
                .replaceAll("`([^`]+)`", "$1")
                // 链接 [text](url)
                .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1")
                // 图片 ![alt](url)
                .replaceAll("!\\[([^\\]]*)\\]\\([^)]+\\)", "[图片: $1]")
                .trim();
    }

    private String buildFileName(String title) {
        String safeName = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (safeName.length() > 40) safeName = safeName.substring(0, 40);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String shortId = UUID.randomUUID().toString().substring(0, 6);
        return safeName + "_" + timestamp + "_" + shortId + ".docx";
    }
}