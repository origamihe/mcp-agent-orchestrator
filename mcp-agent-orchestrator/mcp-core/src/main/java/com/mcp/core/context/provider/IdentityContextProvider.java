package com.mcp.core.context.provider;

import com.mcp.core.context.BuildContext;
import com.mcp.core.context.ContextProvider;
import com.mcp.core.context.PromptContext;
import org.springframework.stereotype.Component;

/**
 * 身份上下文提供者 — 填充身份相关层（identity、character、relationship、persona）。
 *
 * 设计原则：
 * - Persona 不再是一个大 blob，而是拆分为多个独立层
 * - IDENTITY：核心身份声明（"我是澪音"）
 * - CHARACTER：说话风格、语气、价值观
 * - RELATIONSHIP：与用户的关系（当前从 Persona 行为准则提取，未来独立管理）
 * - PERSONA：完整人格文本（向后兼容，其他 Policy 可选择性包含）
 *
 * 纯函数：所有数据由 Orchestrator 通过 BuildContext.personaPrompt() 传入，
 * 不直接查询任何外部服务。
 *
 * 优先级：
 * 1. BuildContext 中由 Orchestrator 预加载的 persona 人格设定
 * 2. 回退：开发者设定 + 基础系统提示
 */
@Component
public class IdentityContextProvider implements ContextProvider {

    @Override
    public void collect(PromptContext.PromptContextBuilder builder, BuildContext ctx) {
        String personaText = ctx.personaPrompt();
        if (personaText != null && !personaText.isEmpty()) {
            builder.persona(personaText);
            builder.identity(extractIdentity(personaText));
            builder.character(extractCharacter(personaText));
            builder.relationship(extractRelationship(personaText));
        } else {
            String fallback = buildFallbackPersona(
                    ctx.developerPrompt(), ctx.personaPrompt(), ctx.baseSystemPrompt());
            builder.persona(fallback);
            builder.identity(fallback);
        }
    }

    private String extractIdentity(String personaText) {
        int identityStart = personaText.indexOf("【核心身份】");
        if (identityStart < 0) {
            return personaText;
        }
        int behaviorStart = personaText.indexOf("【行为准则】", identityStart);
        if (behaviorStart > identityStart) {
            return personaText.substring(identityStart, behaviorStart).trim();
        }
        return personaText.substring(identityStart).trim();
    }

    private String extractCharacter(String personaText) {
        int behaviorStart = personaText.indexOf("【行为准则】");
        if (behaviorStart < 0) {
            return "";
        }
        return personaText.substring(behaviorStart).trim();
    }

    private String extractRelationship(String personaText) {
        return "";
    }

    private String buildFallbackPersona(String developerPrompt, String personaPrompt, String baseSystemPrompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("【核心身份】\n");
        sb.append("你是「澪音」，不是其他任何角色。\n\n");

        if (developerPrompt != null && !developerPrompt.isEmpty()) {
            sb.append("【开发者设定】\n").append(developerPrompt).append("\n\n");
        }

        if (personaPrompt != null && !personaPrompt.isEmpty()) {
            sb.append("【人格设定】\n").append(personaPrompt).append("\n\n");
        } else if (baseSystemPrompt != null && !baseSystemPrompt.isEmpty()) {
            sb.append("【人格设定】\n").append(baseSystemPrompt).append("\n\n");
        }
        return sb.toString();
    }
}