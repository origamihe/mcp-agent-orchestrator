package com.mcp.tools.tool;

/**
 * 统一文档生成器接口 — 所有文档生成器（DOCX/PPT/XLSX/PDF/HTML）的抽象。
 *
 * 设计原则：
 * 1. 统一的输入模型：接受 LLM 生成的 JSON 内容 + 用户标题
 * 2. 统一的输出模型：返回文件名、下载 URL、MIME 类型、大小
 * 3. 新增加文档类型只需实现此接口并注册为 Spring Bean
 *
 * 当前实现：
 * - DocxGeneratorTool (implements DocumentGenerator)
 * - PptGeneratorTool  (implements DocumentGenerator)
 * - XlsxGeneratorTool (implements DocumentGenerator)  ← 新增
 * - PdfGeneratorTool  (implements DocumentGenerator)  ← 新增
 * - HtmlGeneratorTool (implements DocumentGenerator)  ← 新增
 */
public interface DocumentGenerator {

    record DocResult(String fileName, String downloadUrl, String mimeType,
                     long size, String message) {}

    /**
     * 从 LLM JSON 响应和用户标题生成文档。
     */
    DocResult generate(String llmResponse, String userTitle) throws Exception;

    /**
     * 从 Markdown 文本直接生成文档（纯渲染器模式）。
     */
    DocResult generateFromMarkdown(String markdownContent, String title) throws Exception;

    /**
     * 是否支持指定格式。
     */
    boolean supports(String format);
}