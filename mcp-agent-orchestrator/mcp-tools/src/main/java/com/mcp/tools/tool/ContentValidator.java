package com.mcp.tools.tool;

import com.mcp.tools.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ContentValidator {

    private static final int MAX_BULLETS_PER_SLIDE = 6;
    private static final int MAX_BULLET_LENGTH = 200;
    private static final int MAX_PARAGRAPH_LENGTH = 2000;
    private static final int MAX_TABLE_ROWS = 20;

    /** 校验 DOCX 文档模型 */
    public ValidationResult validateDocx(DocumentModel model) {
        var errors = new ArrayList<String>();
        var warnings = new ArrayList<String>();

        if (model.title() == null || model.title().isBlank()) {
            errors.add("文档标题不能为空");
        }
        if (model.sections() == null || model.sections().isEmpty()) {
            errors.add("文档至少需要一个章节");
            return ValidationResult.failure(errors);
        }

        for (int i = 0; i < model.sections().size(); i++) {
            var section = model.sections().get(i);
            if (section.blocks() == null || section.blocks().isEmpty()) {
                warnings.add("章节 " + (i + 1) + "（" + section.title() + "）没有内容块");
            }
            for (var block : section.blocks()) {
                validateBlock(block, errors, warnings, "章节 " + (i + 1));
            }
        }

        return ValidationResult.of(errors, warnings);
    }

    /** 校验 PPT 文档模型 */
    public ValidationResult validatePpt(DocumentModel.PptModel model) {
        var errors = new ArrayList<String>();
        var warnings = new ArrayList<String>();

        if (model.title() == null || model.title().isBlank()) {
            errors.add("PPT 标题不能为空");
        }
        if (model.slides() == null || model.slides().isEmpty()) {
            errors.add("PPT 至少需要一页幻灯片");
            return ValidationResult.failure(errors);
        }

        for (int i = 0; i < model.slides().size(); i++) {
            var slide = model.slides().get(i);
            if (slide.blocks() == null || slide.blocks().isEmpty()) {
                warnings.add("第 " + (i + 1) + " 页（" + slide.title() + "）没有内容");
            }

            int bulletCount = 0;
            for (var block : slide.blocks()) {
                validateBlock(block, errors, warnings, "第 " + (i + 1) + " 页");
                if (block instanceof Block.BulletList bl) {
                    bulletCount += bl.items().size();
                }
            }

            if (bulletCount > MAX_BULLETS_PER_SLIDE) {
                warnings.add("第 " + (i + 1) + " 页有 " + bulletCount + " 个要点，建议不超过 " + MAX_BULLETS_PER_SLIDE + " 个");
            }
        }

        return ValidationResult.of(errors, warnings);
    }

    private void validateBlock(Block block, List<String> errors, List<String> warnings, String location) {
        switch (block) {
            case Block.Paragraph p -> {
                if (p.text().length() > MAX_PARAGRAPH_LENGTH) {
                    warnings.add(location + "：段落过长（" + p.text().length() + " 字符）");
                }
            }
            case Block.BulletList bl -> {
                for (int j = 0; j < bl.items().size(); j++) {
                    if (bl.items().get(j).length() > MAX_BULLET_LENGTH) {
                        warnings.add(location + "：第 " + (j + 1) + " 个要点过长（" + bl.items().get(j).length() + " 字符）");
                    }
                }
            }
            case Block.OrderedList ol -> {
                for (int j = 0; j < ol.items().size(); j++) {
                    if (ol.items().get(j).length() > MAX_BULLET_LENGTH) {
                        warnings.add(location + "：第 " + (j + 1) + " 个列表项过长（" + ol.items().get(j).length() + " 字符）");
                    }
                }
            }
            case Block.Table t -> {
                if (t.rows().size() > MAX_TABLE_ROWS) {
                    warnings.add(location + "：表格行数过多（" + t.rows().size() + " 行）");
                }
            }
            default -> {}
        }
    }
}