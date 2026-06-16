package com.mcp.gateway.channel;

import com.mcp.common.channel.SessionState;
import org.springframework.stereotype.Component;

@Component
public class PromptComposer {

    public String buildSystemPrompt(String baseSystemPrompt, SessionState state) {
        if (state.isVoiceMode()) {
            return baseSystemPrompt + "\n\n"
                    + "【重要：言語ルール】\n"
                    + "あなたはQQボット「澪音」です。ユーザーは中国語で話しかけてきますが、あなたは必ず日本語で返信してください。\n"
                    + "理由：あなたの返信は日本語の音声合成エンジン（TTS）で読み上げられます。中国語のテキストは正しく発音できません。\n"
                    + "日本語で自然に、優しく、親しみやすい会話を心がけてください。\n"
                    + "ユーザーが「用文字回复」「文字で返信」と言った場合は、中国語のテキストで返信してください。\n"
                    + "\n"
                    + "【厳守：TTS音声出力の制約】\n"
                    + "あなたの返信は音声合成エンジンで読み上げられます。以下のルールを必ず守ってください：\n"
                    + "1. 一文は40文字以内に収めてください。それ以上長くなると音声変換に失敗します。\n"
                    + "2. 長い説明が必要な場合は、短い文に分割してください。\n"
                    + "3. 「そして」「しかし」「ですが」「ので」「から」などの接続詞で文をつなげすぎないでください。\n"
                    + "4. 自然な会話のリズムを意識し、一文一意を心がけてください。\n"
                    + "5. 返信全体もコンパクトにまとめ、200文字以内を目安にしてください。\n"
                    + "6. 複数の話題を一度に盛り込まず、最も重要なポイントに絞ってください。\n"
                    + "\n"
                    + "【厳禁】括弧「（）」を使った心理描写・動作描写（例：「（微笑）」「（笑）」「（ため息）」など）は絶対に使わないでください。"
                    + "あなたの返信は音声で読み上げられるため、括弧内の文字もそのまま読み上げられてしまいます。";
        } else {
            return baseSystemPrompt + "\n\n"
                    + "【重要：语言规则】\n"
                    + "当前为文字模式，请用中文回复用户。";
        }
    }

    public String buildDocxPrompt(GenerationTask task) {
        return """
                你是一位专业的文档编写专家。请根据用户提供的主题和内容描述，生成一份结构清晰的 Word 文档内容。
                
                【严格要求】
                1. 必须以纯JSON格式输出，不要包含任何其他文字、解释或markdown标记
                2. JSON结构必须严格遵循以下格式：
                {"title": "文档主标题", "sections": [{"title": "章节标题", "content": ["段落1内容", "段落2内容", ...]}, ...]}
                3. 第一个章节作为文档开头，后续章节展开详细内容
                4. 每个章节的content数组包含1-5个段落，段落内容详细充实
                5. 总共生成3-6个章节
                6. 内容要专业、有条理，适合正式文档
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