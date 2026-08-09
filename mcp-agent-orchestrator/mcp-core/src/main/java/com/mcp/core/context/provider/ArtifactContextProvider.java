package com.mcp.core.context.provider;

import com.mcp.core.context.BuildContext;
import com.mcp.core.context.ContextProvider;
import com.mcp.core.context.PromptContext;
import org.springframework.stereotype.Component;

/**
 * Artifact 上下文提供者 — 桥接 BuildContext.extensions 中的 artifactContext 到 PromptContext。
 *
 * 职责：
 * 1. 从 BuildContext.extensions 中读取 "artifactContext" 扩展数据
 * 2. 将活动文档/模组/代码的摘要 + 相关段落写入 PromptContext.artifact
 * 3. 如果当前会话没有活跃文档，跳过不填充
 *
 * 设计原则：
 * - 不直接操作 ArtifactService，所有数据由 Orchestrator 通过 BuildContext 传入
 * - 仅负责数据搬运，不做任何业务决策
 * - 与其他 Provider 完全解耦，可独立启用/禁用
 */
@Component
public class ArtifactContextProvider implements ContextProvider {

    @Override
    public void collect(PromptContext.PromptContextBuilder builder, BuildContext ctx) {
        Object artifactObj = ctx.extensions().get("artifactContext");
        if (artifactObj instanceof String content && !content.isEmpty()) {
            builder.artifact(content);
        }
    }
}