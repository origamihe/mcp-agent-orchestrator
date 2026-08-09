package com.mcp.gateway.channel;

import org.springframework.stereotype.Component;

@Component
public class PromptComposer {

    public String buildDocxPrompt(GenerationTask task) {
        return """
                你是一位专业的文档编写专家。请根据用户提供的主题，生成一份结构清晰、内容详实的 Word 文档内容。
                
                如果用户要求搜索某个主题的最新信息，请基于你的知识库中相关的最新信息来撰写文档内容，
                确保内容专业、准确、有深度。
                
                【严格要求】
                1. 必须以纯JSON格式输出，不要包含任何其他文字、解释或markdown标记
                2. JSON结构必须严格遵循以下格式：
                {"title": "文档主标题", "sections": [{"title": "章节标题", "content": ["段落1内容", "段落2内容", ...]}, ...]}
                3. 第一个章节作为文档概述/背景介绍，后续章节展开详细内容
                4. 每个章节的content数组包含1-5个段落，段落内容详细充实，每个段落至少3-4句话
                5. 总共生成3-6个章节，内容要覆盖主题的多个维度
                6. 内容要专业、有条理，适合正式文档，包含具体数据和事实
                7. 只输出JSON，不要输出```json```等标记
                
                文档主题：%s
                """.formatted(task.topic());
    }

    public String buildPptPrompt(GenerationTask task) {
        return """
                你是一位专业的 PPT 演示文稿制作专家。请根据用户提供的主题，生成一份结构清晰的 PPT 内容。
                
                【严格要求】
                1. 必须以纯JSON格式输出，不要包含任何其他文字、解释或markdown标记
                2. JSON结构必须严格遵循以下格式：
                {"title": "PPT主标题", "subtitle": "副标题", "slides": [{"title": "幻灯片标题", "content": ["要点1", "要点2", ...]}, ...]}
                3. 第一张幻灯片作为封面/概述，后续幻灯片展开详细内容
                4. 每张幻灯片content包含3-5个要点，每个要点简洁明了
                5. 总共生成5-10张幻灯片
                6. 内容要专业、有条理，适合演示场合
                7. 只输出JSON，不要输出```json```等标记
                
                PPT主题：%s
                """.formatted(task.topic());
    }
}