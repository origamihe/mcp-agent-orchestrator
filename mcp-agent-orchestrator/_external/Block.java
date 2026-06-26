package com.mcp.tools.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/**
 * 文档块——中间文档模型的基本单元。
 * 使用 Jackson 多态反序列化，让 LLM 可以直接输出此结构。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Block.Heading.class, name = "heading"),
        @JsonSubTypes.Type(value = Block.Paragraph.class, name = "paragraph"),
        @JsonSubTypes.Type(value = Block.BulletList.class, name = "bullet_list"),
        @JsonSubTypes.Type(value = Block.OrderedList.class, name = "ordered_list"),
        @JsonSubTypes.Type(value = Block.Table.class, name = "table"),
        @JsonSubTypes.Type(value = Block.CodeBlock.class, name = "code_block"),
        @JsonSubTypes.Type(value = Block.Quote.class, name = "quote"),
        @JsonSubTypes.Type(value = Block.Image.class, name = "image"),
})
public sealed interface Block {

    /** 标题块 */
    record Heading(int level, String text) implements Block {}

    /** 普通段落 */
    record Paragraph(String text) implements Block {}

    /** 无序列表 */
    record BulletList(List<String> items) implements Block {}

    /** 有序列表 */
    record OrderedList(List<String> items) implements Block {}

    /** 表格 */
    record Table(List<String> headers, List<List<String>> rows) implements Block {}

    /** 代码块 */
    record CodeBlock(String language, String code) implements Block {}

    /** 引用块 */
    record Quote(String text, String attribution) implements Block {}

    /** 图片占位 */
    record Image(String alt, String url, String caption) implements Block {}
}