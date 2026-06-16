package com.mcp.tools.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;

/**
 * 中间文档模型——LLM 输出 → DocumentModel → Office 渲染。
 * 统一的 schema，DOCX 和 PPT 共用基础结构，各取所需。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentModel(
        String title,
        String type,           // "docx" | "pptx"
        String theme,          // "default" | "academic" | "business" | "minimal" | "report"
        List<Section> sections // DOCX 用 sections
) {

    /**
     * 章节——DOCX 的顶层结构单元
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Section(
            String title,
            int level,              // 1=一级标题, 2=二级标题...
            List<Block> blocks       // 该章节下的内容块
    ) {}

    /**
     * 幻灯片——PPT 的顶层结构单元
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Slide(
            String title,
            SlideType layout,       // 页面类型
            List<Block> blocks,     // 该页的内容块
            String speakerNotes     // 演讲者备注
    ) {}

    // ===== PPT 专用：从 slides 构建 =====
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PptModel(
            String title,
            String theme,
            List<Slide> slides
    ) {}
}