package com.mcp.engine.artifact;

import com.mcp.common.artifact.Artifact;
import com.mcp.common.artifact.ArtifactType;
import com.mcp.common.artifact.ConversationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReferenceResolver — 语义引用解析器。
 *
 * 职责：将用户的模糊引用（"这个"、"它"、"上一份"、"刚才那个SQL"等）
 * 精确解析为 ConversationContext 中的 Artifact。
 *
 * 这是 P0 的核心组件，解决 Follow-up 对话中 LLM 只能靠猜的问题。
 *
 * 两阶段解析：
 * 1. 语义匹配：从 ConversationContext 中按类型关键词匹配
 * 2. 模糊兜底：返回最近的 Artifact（lastArtifact）
 */
@Slf4j
@Component
public class ReferenceResolver {

    private static final Pattern REFERENCE_PATTERN = Pattern.compile(
            "(这个|那个|它|其|该|上次|刚刚|刚才|上一份|这份|那份|前面的|之前的)" +
                    "(?:的)?(?:那个|这个)?" +
                    "(代码|prompt|markdown|报告|总结|摘要|SQL|图片|图像|搜索|工具|文件|文档|内容|结果)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TYPE_KEYWORD_PATTERN = Pattern.compile(
            "(代码|code|prompt|提示词|markdown|md|报告|report|总结|摘要|summary|" +
                    "SQL|sql|图片|图像|image|搜索|search|工具|tool|文件|file|文档|document)",
            Pattern.CASE_INSENSITIVE);

    /**
     * 解析用户请求中的引用，返回匹配的 Artifact。
     *
     * @param userRequest 用户请求文本
     * @param ctx         当前对话上下文
     * @param artifactService Artifact 服务（用于加载完整内容）
     * @return 匹配到的 Artifact（含完整内容），或空
     */
    public Optional<Artifact> resolve(String userRequest, ConversationContext ctx,
                                       ArtifactService artifactService) {
        if (userRequest == null || userRequest.isBlank() || ctx == null || ctx.isEmpty()) {
            return Optional.empty();
        }

        Matcher refMatcher = REFERENCE_PATTERN.matcher(userRequest);
        if (!refMatcher.find()) {
            return Optional.empty();
        }

        String referenceWord = refMatcher.group(1);
        String typeKeyword = refMatcher.group(2);

        log.info("[ReferenceResolver] Detected reference: word='{}', type='{}' in request: {}",
                referenceWord, typeKeyword, userRequest);

        Optional<ConversationContext.ArtifactRef> resolvedRef;

        if (typeKeyword != null && !typeKeyword.isBlank()) {
            resolvedRef = resolveByTypeKeyword(typeKeyword, ctx);
        } else {
            resolvedRef = ctx.resolve(userRequest);
        }

        if (resolvedRef.isEmpty()) {
            resolvedRef = Optional.ofNullable(ctx.getLastArtifact());
        }

        if (resolvedRef.isPresent()) {
            ConversationContext.ArtifactRef ref = resolvedRef.get();
            log.info("[ReferenceResolver] Resolved to: {}", ref.toDisplayString());

            if (ref.getArtifactId() != null) {
                return artifactService.findById(ref.getArtifactId())
                        .or(() -> {
                            log.warn("[ReferenceResolver] Artifact {} not found in DB, using ref only",
                                    ref.getArtifactId());
                            return Optional.empty();
                        });
            }
        }

        return Optional.empty();
    }

    /**
     * 检测用户请求是否包含引用语义。
     */
    public boolean containsReference(String userRequest) {
        if (userRequest == null || userRequest.isBlank()) return false;
        return REFERENCE_PATTERN.matcher(userRequest).find();
    }

    /**
     * 从用户请求中提取类型关键词，用于在 ConversationContext 中精确匹配。
     */
    public Optional<String> extractTypeKeyword(String userRequest) {
        if (userRequest == null || userRequest.isBlank()) return Optional.empty();
        Matcher matcher = TYPE_KEYWORD_PATTERN.matcher(userRequest);
        if (matcher.find()) {
            return Optional.of(matcher.group(1).toLowerCase());
        }
        return Optional.empty();
    }

    private Optional<ConversationContext.ArtifactRef> resolveByTypeKeyword(
            String keyword, ConversationContext ctx) {
        if (keyword == null) return Optional.empty();
        String lower = keyword.toLowerCase();

        if (lower.contains("代码") || lower.contains("code"))
            return Optional.ofNullable(ctx.getLastCode());
        if (lower.contains("prompt") || lower.contains("提示词"))
            return Optional.ofNullable(ctx.getLastPrompt());
        if (lower.contains("markdown") || lower.contains("md"))
            return Optional.ofNullable(ctx.getLastMarkdown());
        if (lower.contains("报告") || lower.contains("report"))
            return Optional.ofNullable(ctx.getLastReport());
        if (lower.contains("总结") || lower.contains("摘要") || lower.contains("summary"))
            return Optional.ofNullable(ctx.getLastSummary());
        if (lower.contains("sql"))
            return Optional.ofNullable(ctx.getLastSQL());
        if (lower.contains("图片") || lower.contains("图像") || lower.contains("image"))
            return Optional.ofNullable(ctx.getLastImage());
        if (lower.contains("搜索") || lower.contains("search"))
            return Optional.ofNullable(ctx.getLastSearchResult());
        if (lower.contains("工具") || lower.contains("tool"))
            return Optional.ofNullable(ctx.getLastToolResult());
        if (lower.contains("文件") || lower.contains("file") || lower.contains("文档") || lower.contains("document"))
            return Optional.ofNullable(ctx.getLastArtifact());

        return Optional.empty();
    }
}