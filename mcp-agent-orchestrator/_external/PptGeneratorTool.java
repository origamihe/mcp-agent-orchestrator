package com.mcp.tools.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcp.tools.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PptGeneratorTool {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ContentValidator contentValidator;

    @Value("${ppt.output.dir:./generated/ppt}")
    private String outputDir;

    private static final int MAX_BULLETS_PER_SLIDE = 6;
    private static final int MAX_BULLET_LENGTH = 180;

    public record PptResult(String fileName, String downloadUrl, String mimeType,
                            long size, String message) {}

    /**
     * 三步流程：解析 → 校验 → 渲染
     */
    public PptResult generatePptx(String llmResponse, String userTitle) throws Exception {
        Path dir = Path.of(outputDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        DocumentModel.PptModel model = parseToPptModel(llmResponse, userTitle);

        ValidationResult validation = contentValidator.validatePpt(model);
        if (!validation.valid()) {
            log.warn("PPT validation errors: {}", validation.errors());
            throw new IllegalArgumentException("PPT 内容校验失败: " + String.join("; ", validation.errors()));
        }
        if (!validation.warnings().isEmpty()) {
            log.info("PPT validation warnings: {}", validation.warnings());
        }

        DocumentTheme.PptThemeConfig theme = (DocumentTheme.PptThemeConfig)
                DocumentTheme.of(model.theme());

        String fileName = buildFileName(model.title());
        Path outputPath = dir.resolve(fileName);

        int slideCount = renderPptx(model, theme, outputPath);

        long fileSize = Files.size(outputPath);
        String downloadUrl = "/mcp/download/ppt/" + fileName;
        String message = String.format("PPT 已生成！主题：%s，共 %d 页幻灯片。",
                model.title(), slideCount);

        log.info("PPT generated: {} ({} slides, {} bytes)", outputPath, slideCount, fileSize);
        return new PptResult(fileName, downloadUrl,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                fileSize, message);
    }

    // ===== 第一步：解析 =====

    private DocumentModel.PptModel parseToPptModel(String llmResponse, String userTitle) {
        try {
            String json = extractJson(llmResponse);
            json = normalizeContentToBlocks(json);
            return objectMapper.readValue(json, DocumentModel.PptModel.class);
        } catch (Exception e) {
            log.warn("Failed to parse PptModel from LLM response, using fallback: {}", e.getMessage());
            return buildFallbackPptModel(llmResponse, userTitle);
        }
    }

    /**
     * 将 LLM 输出的简化 content 数组转换为 blocks 格式。
     * "content": ["要点1", "要点2"] → "blocks": [{"type":"bullet_list","items":["要点1","要点2"]}]
     */
    private String normalizeContentToBlocks(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode slidesNode = root.get("slides");
            if (slidesNode != null && slidesNode.isArray()) {
                for (JsonNode slide : slidesNode) {
                    if (slide.has("content") && !slide.has("blocks")) {
                        JsonNode contentNode = slide.get("content");
                        if (contentNode.isArray()) {
                            var blocksArray = objectMapper.createArrayNode();
                            var bulletBlock = objectMapper.createObjectNode();
                            bulletBlock.put("type", "bullet_list");
                            bulletBlock.set("items", contentNode);
                            blocksArray.add(bulletBlock);
                            ((ObjectNode) slide).set("blocks", blocksArray);
                            ((ObjectNode) slide).remove("content");
                        }
                    }
                }
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.debug("Failed to normalize content to blocks: {}", e.getMessage());
            return json;
        }
    }

    /** 安全获取 blocks，避免 NPE */
    private static List<Block> safeBlocks(DocumentModel.Slide slide) {
        List<Block> blocks = slide.blocks();
        return blocks != null ? blocks : Collections.emptyList();
    }

    // ===== 第二步：校验（委托给 ContentValidator） =====

    // ===== 第三步：渲染 =====

    private int renderPptx(DocumentModel.PptModel model, DocumentTheme.PptThemeConfig theme,
                           Path outputPath) throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            ppt.setPageSize(theme.slideSize());
            XSLFSlideMaster master = ppt.getSlideMasters().get(0);

            for (DocumentModel.Slide slide : model.slides()) {
                renderSlide(ppt, master, slide, model.title(), theme);
            }

            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                ppt.write(fos);
            }

            return model.slides().size();
        }
    }

    private void renderSlide(XMLSlideShow ppt, XSLFSlideMaster master,
                             DocumentModel.Slide slide, String pptTitle,
                             DocumentTheme.PptThemeConfig theme) {
        SlideType type = slide.layout() != null ? slide.layout() : SlideType.BULLET;

        switch (type) {
            case COVER -> renderCoverSlide(ppt, master, slide, pptTitle, theme);
            case AGENDA -> renderAgendaSlide(ppt, master, slide, theme);
            case SECTION_HEADER -> renderSectionHeader(ppt, master, slide, theme);
            case BULLET -> renderBulletSlide(ppt, master, slide, theme);
            case TWO_COLUMN -> renderTwoColumnSlide(ppt, master, slide, theme);
            case CONCLUSION -> renderConclusionSlide(ppt, master, slide, theme);
            default -> renderBulletSlide(ppt, master, slide, theme);
        }
    }

    private void renderCoverSlide(XMLSlideShow ppt, XSLFSlideMaster master,
                                  DocumentModel.Slide slide, String pptTitle,
                                  DocumentTheme.PptThemeConfig theme) {
        XSLFSlideLayout layout = master.getLayout(SlideLayout.TITLE);
        XSLFSlide s = ppt.createSlide(layout);

        XSLFTextShape titleShape = s.getPlaceholder(0);
        if (titleShape != null) {
            titleShape.clearText();
            XSLFTextParagraph p = titleShape.addNewTextParagraph();
            p.setTextAlign(TextParagraph.TextAlign.CENTER);
            XSLFTextRun r = p.addNewTextRun();
            r.setText(pptTitle);
            r.setFontSize((double) theme.coverTitleSize());
            r.setBold(true);
            r.setFontColor(theme.titleColor());
            r.setFontFamily(theme.fontFamily());
        }

        XSLFTextShape subtitleShape = s.getPlaceholder(1);
        if (subtitleShape != null && slide.title() != null && !slide.title().isBlank()) {
            subtitleShape.clearText();
            XSLFTextParagraph sp = subtitleShape.addNewTextParagraph();
            sp.setTextAlign(TextParagraph.TextAlign.CENTER);
            XSLFTextRun sr = sp.addNewTextRun();
            sr.setText(slide.title());
            sr.setFontSize((double) theme.smallFontSize());
            sr.setFontColor(theme.accentColor());
            sr.setFontFamily(theme.fontFamily());
        }
    }

    private void renderAgendaSlide(XMLSlideShow ppt, XSLFSlideMaster master,
                                   DocumentModel.Slide slide,
                                   DocumentTheme.PptThemeConfig theme) {
        XSLFSlideLayout layout = master.getLayout(SlideLayout.TITLE_AND_CONTENT);
        XSLFSlide s = ppt.createSlide(layout);

        setSlideTitle(s, slide.title(), "目录", theme);

        XSLFTextShape body = s.getPlaceholder(1);
        if (body != null) {
            body.clearText();
            for (Block block : safeBlocks(slide)) {
                if (block instanceof Block.BulletList bl) {
                    for (int i = 0; i < bl.items().size(); i++) {
                        XSLFTextParagraph bp = body.addNewTextParagraph();
                        bp.setBullet(false);
                        XSLFTextRun br = bp.addNewTextRun();
                        br.setText((i + 1) + ". " + bl.items().get(i));
                        br.setFontSize((double) theme.bodyFontSize());
                        br.setFontColor(theme.bodyColor());
                        br.setFontFamily(theme.fontFamily());
                    }
                }
            }
        }
    }

    private void renderSectionHeader(XMLSlideShow ppt, XSLFSlideMaster master,
                                     DocumentModel.Slide slide,
                                     DocumentTheme.PptThemeConfig theme) {
        XSLFSlideLayout layout = master.getLayout(SlideLayout.TITLE_ONLY);
        XSLFSlide s = ppt.createSlide(layout);

        XSLFTextShape titleShape = s.getPlaceholder(0);
        if (titleShape != null) {
            titleShape.clearText();
            XSLFTextParagraph p = titleShape.addNewTextParagraph();
            p.setTextAlign(TextParagraph.TextAlign.CENTER);
            XSLFTextRun r = p.addNewTextRun();
            r.setText(slide.title() != null ? slide.title() : "");
            r.setFontSize((double) theme.coverTitleSize());
            r.setBold(true);
            r.setFontColor(theme.titleColor());
            r.setFontFamily(theme.fontFamily());
        }
    }

    private void renderBulletSlide(XMLSlideShow ppt, XSLFSlideMaster master,
                                   DocumentModel.Slide slide,
                                   DocumentTheme.PptThemeConfig theme) {
        List<DocumentModel.Slide> splitSlides = splitSlideIfNeeded(slide, theme);
        for (DocumentModel.Slide subSlide : splitSlides) {
            XSLFSlideLayout layout = master.getLayout(SlideLayout.TITLE_AND_CONTENT);
            XSLFSlide s = ppt.createSlide(layout);

            setSlideTitle(s, subSlide.title(), slide.title(), theme);

            XSLFTextShape body = s.getPlaceholder(1);
            if (body != null) {
                body.clearText();
                for (Block block : safeBlocks(subSlide)) {
                    if (block instanceof Block.BulletList bl) {
                        for (String item : bl.items()) {
                            String displayText = item.length() > MAX_BULLET_LENGTH
                                    ? item.substring(0, MAX_BULLET_LENGTH) + "…"
                                    : item;
                            XSLFTextParagraph bp = body.addNewTextParagraph();
                            bp.setBullet(true);
                            bp.setIndentLevel(1);
                            XSLFTextRun br = bp.addNewTextRun();
                            br.setText(displayText);
                            br.setFontSize((double) theme.bodyFontSize());
                            br.setFontColor(theme.bodyColor());
                            br.setFontFamily(theme.fontFamily());
                        }
                    } else if (block instanceof Block.Paragraph p) {
                        XSLFTextParagraph bp = body.addNewTextParagraph();
                        XSLFTextRun br = bp.addNewTextRun();
                        br.setText(p.text());
                        br.setFontSize((double) theme.bodyFontSize());
                        br.setFontColor(theme.bodyColor());
                        br.setFontFamily(theme.fontFamily());
                    }
                }
            }
        }
    }

    private void renderTwoColumnSlide(XMLSlideShow ppt, XSLFSlideMaster master,
                                      DocumentModel.Slide slide,
                                      DocumentTheme.PptThemeConfig theme) {
        XSLFSlide s = ppt.createSlide();

        int leftX = 50, rightX = 500, colWidth = 400, y = 100, rowHeight = 30;

        setSlideTitle(s, slide.title(), "对比", theme);

        List<Block> blocks = safeBlocks(slide);
        if (blocks.size() >= 2) {
            renderColumnBlock(s, blocks.get(0), leftX, y, colWidth, theme);
            renderColumnBlock(s, blocks.get(1), rightX, y, colWidth, theme);
        }
    }

    private void renderColumnBlock(XSLFSlide s, Block block, int x, int y, int width,
                                   DocumentTheme.PptThemeConfig theme) {
        XSLFTextBox box = s.createTextBox();
        box.setAnchor(new java.awt.Rectangle(x, y, width, 400));
        if (block instanceof Block.BulletList bl) {
            for (String item : bl.items()) {
                XSLFTextParagraph p = box.addNewTextParagraph();
                p.setBullet(true);
                XSLFTextRun r = p.addNewTextRun();
                r.setText(item);
                r.setFontSize((double) theme.bodyFontSize());
                r.setFontColor(theme.bodyColor());
                r.setFontFamily(theme.fontFamily());
            }
        }
    }

    private void renderConclusionSlide(XMLSlideShow ppt, XSLFSlideMaster master,
                                       DocumentModel.Slide slide,
                                       DocumentTheme.PptThemeConfig theme) {
        XSLFSlideLayout layout = master.getLayout(SlideLayout.TITLE_AND_CONTENT);
        XSLFSlide s = ppt.createSlide(layout);

        setSlideTitle(s, slide.title(), "总结", theme);

        XSLFTextShape body = s.getPlaceholder(1);
        if (body != null) {
            body.clearText();
            for (Block block : safeBlocks(slide)) {
                if (block instanceof Block.BulletList bl) {
                    for (String item : bl.items()) {
                        XSLFTextParagraph bp = body.addNewTextParagraph();
                        bp.setBullet(true);
                        bp.setIndentLevel(1);
                        XSLFTextRun br = bp.addNewTextRun();
                        br.setText(item);
                        br.setFontSize((double) theme.bodyFontSize());
                        br.setFontColor(theme.accentColor());
                        br.setBold(true);
                        br.setFontFamily(theme.fontFamily());
                    }
                }
            }
        }
    }

    /**
     * 自动分页：当 bullet 数量超过阈值时，拆分为多页
     */
    private List<DocumentModel.Slide> splitSlideIfNeeded(DocumentModel.Slide slide,
                                                         DocumentTheme.PptThemeConfig theme) {
        List<DocumentModel.Slide> result = new ArrayList<>();
        List<String> allBullets = new ArrayList<>();

        for (Block block : safeBlocks(slide)) {
            if (block instanceof Block.BulletList bl) {
                allBullets.addAll(bl.items());
            }
        }

        if (allBullets.size() <= MAX_BULLETS_PER_SLIDE) {
            result.add(slide);
            return result;
        }

        int pageNum = 0;
        for (int i = 0; i < allBullets.size(); i += MAX_BULLETS_PER_SLIDE) {
            int end = Math.min(i + MAX_BULLETS_PER_SLIDE, allBullets.size());
            List<String> pageBullets = allBullets.subList(i, end);
            String pageTitle = pageNum == 0
                    ? slide.title()
                    : slide.title() + "（续" + (pageNum + 1) + "）";
            result.add(new DocumentModel.Slide(
                    pageTitle,
                    SlideType.BULLET,
                    List.of(new Block.BulletList(pageBullets)),
                    null
            ));
            pageNum++;
        }

        return result;
    }

    private void setSlideTitle(XSLFSlide s, String title, String fallback,
                               DocumentTheme.PptThemeConfig theme) {
        XSLFTextShape titleShape = s.getPlaceholder(0);
        if (titleShape == null) return;
        titleShape.clearText();
        String displayTitle = (title != null && !title.isBlank()) ? title : fallback;
        if (displayTitle == null || displayTitle.isBlank()) return;
        XSLFTextParagraph tp = titleShape.addNewTextParagraph();
        XSLFTextRun tr = tp.addNewTextRun();
        tr.setText(displayTitle);
        tr.setFontSize((double) theme.slideTitleSize());
        tr.setBold(true);
        tr.setFontColor(theme.titleColor());
        tr.setFontFamily(theme.fontFamily());
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
     * 升级版 fallback：识别 Markdown 标题为 slide，列表为 bullet，
     * 返回结构化的 PptModel。
     * 当 LLM 返回非结构化中文文本时，按句子/段落拆分生成多页幻灯片。
     */
    private DocumentModel.PptModel buildFallbackPptModel(String response, String userTitle) {
        String[] lines = response.split("\n");
        List<DocumentModel.Slide> slides = new ArrayList<>();
        var currentBullets = new ArrayList<String>();
        String currentTitle = userTitle;
        boolean isFirstSlide = true;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("#")) {
                if (!currentBullets.isEmpty() || isFirstSlide) {
                    slides.add(new DocumentModel.Slide(
                            currentTitle,
                            isFirstSlide ? SlideType.COVER : SlideType.BULLET,
                            currentBullets.isEmpty()
                                    ? List.of()
                                    : List.of(new Block.BulletList(List.copyOf(currentBullets))),
                            null
                    ));
                    currentBullets = new ArrayList<>();
                    isFirstSlide = false;
                }
                currentTitle = line.replaceFirst("^#+\\s*", "");
            } else if (line.startsWith("-") || line.startsWith("*")) {
                if (isFirstSlide) {
                    isFirstSlide = false;
                }
                currentBullets.add(line.replaceFirst("^[-*]\\s*", ""));
            } else {
                if (isFirstSlide) {
                    isFirstSlide = false;
                }
                currentBullets.add(line);
            }
        }

        if (!currentBullets.isEmpty() || isFirstSlide) {
            slides.add(new DocumentModel.Slide(
                    currentTitle,
                    isFirstSlide ? SlideType.COVER : SlideType.BULLET,
                    currentBullets.isEmpty()
                            ? List.of()
                            : List.of(new Block.BulletList(List.copyOf(currentBullets))),
                    null
            ));
        }

        // 如果仍然没有有效 slides（LLM 返回了完全无法解析的文本），
        // 按中文句子/段落拆分，生成多页基础幻灯片
        if (slides.isEmpty() || (slides.size() == 1 && slides.get(0).blocks().isEmpty())) {
            slides = buildSlidesFromPlainText(response, userTitle);
        }

        return new DocumentModel.PptModel(userTitle, "business", slides);
    }

    /**
     * 从纯文本中按句子拆分生成幻灯片（最后的兜底策略）
     */
    private List<DocumentModel.Slide> buildSlidesFromPlainText(String text, String userTitle) {
        var slides = new ArrayList<DocumentModel.Slide>();

        // 封面
        slides.add(new DocumentModel.Slide(
                userTitle, SlideType.COVER,
                List.of(new Block.BulletList(List.of(text.length() > 100 ? text.substring(0, 100) + "…" : text))),
                null
        ));

        // 按中文句号、分号、换行拆分
        String[] sentences = text.split("[。；;\n]");
        var pageBullets = new ArrayList<String>();
        int pageIndex = 1;

        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty() || sentence.length() < 2) continue;
            pageBullets.add(sentence);

            if (pageBullets.size() >= MAX_BULLETS_PER_SLIDE) {
                slides.add(new DocumentModel.Slide(
                        userTitle + " - 第" + pageIndex + "页",
                        SlideType.BULLET,
                        List.of(new Block.BulletList(List.copyOf(pageBullets))),
                        null
                ));
                pageBullets = new ArrayList<>();
                pageIndex++;
            }
        }

        if (!pageBullets.isEmpty()) {
            slides.add(new DocumentModel.Slide(
                    userTitle + " - 第" + pageIndex + "页",
                    SlideType.BULLET,
                    List.of(new Block.BulletList(List.copyOf(pageBullets))),
                    null
            ));
        }

        return slides;
    }

    private String buildFileName(String title) {
        String safeName = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (safeName.length() > 40) safeName = safeName.substring(0, 40);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String shortId = UUID.randomUUID().toString().substring(0, 6);
        return safeName + "_" + timestamp + "_" + shortId + ".pptx";
    }
}