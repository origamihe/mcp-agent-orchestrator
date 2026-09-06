package com.mcp.engine.agent.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.llm.client.ChatMessage;
import com.mcp.llm.client.LlmClient;
import com.mcp.tools.model.SearchDocument;
import com.mcp.tools.model.SearchResult;
import com.mcp.tools.search.EvidenceItem;
import com.mcp.tools.search.EvidencePool;
import com.mcp.tools.search.SourceCredibility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ResearchSynthesizer {

    private final LlmClient llmClient;

    public ResearchSynthesizer(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_DOCUMENTS_FOR_SUMMARY = 20;
    private static final int SUMMARY_TARGET_TOKENS = 300;

    /**
     * 完整的研究合成 Pipeline
     * Stage 1 (Map): 对每篇文章独立总结（含时间提取）
     * Stage 2 (Evidence Pool): 构建结构化证据池（含来源评级）
     * Stage 3 (Cross-Check): 交叉验证与冲突检测
     * Stage 4 (Reduce): 生成最终研究报告（含来源追溯）
     */
    public Mono<String> synthesize(String query, List<SearchResult> searchResults, List<SearchDocument> documents) {
        if (llmClient == null) {
            log.warn("[ResearchSynthesizer] LlmClient not configured, skipping synthesis");
            return Mono.just(buildFallbackReport(query, searchResults, documents));
        }

        if (documents == null || documents.isEmpty()) {
            log.info("[ResearchSynthesizer] No documents to synthesize, building basic report from search results");
            return buildBasicReport(query, searchResults);
        }

        log.info("[ResearchSynthesizer] Starting synthesis: query='{}', {} results, {} docs",
                query,
                searchResults != null ? searchResults.size() : 0,
                documents.size());

        return summarizeDocuments(documents)
                .flatMap(summaries -> buildEvidencePool(query, searchResults, documents, summaries))
                .flatMap(this::crossCheck)
                .flatMap(this::generateFinalReport);
    }

    /**
     * Stage 1 (Map): 对每篇文章调用 LLM 进行独立总结（含时间提取）
     */
    private Mono<List<ArticleSummary>> summarizeDocuments(List<SearchDocument> documents) {
        List<SearchDocument> topDocs = documents.stream()
                .limit(MAX_DOCUMENTS_FOR_SUMMARY)
                .toList();

        if (topDocs.isEmpty()) {
            return Mono.just(List.of());
        }

        log.info("[ResearchSynthesizer] Stage 1 (Map): summarizing {} documents", topDocs.size());

        List<Mono<ArticleSummary>> tasks = topDocs.stream()
                .map(doc -> summarizeSingleDocument(doc)
                        .onErrorResume(e -> {
                            log.warn("[ResearchSynthesizer] Summary failed for {}: {}", doc.url(), e.getMessage());
                            return Mono.just(new ArticleSummary(
                                    doc.title(), doc.url(), "Summary unavailable",
                                    List.of(), EvidenceItem.STANCE_NEUTRAL, 0.3, null));
                        }))
                .toList();

        return Flux.merge(tasks).collectList();
    }

    /**
     * 对单篇文章调用 LLM 生成结构化摘要（含时间维度）
     */
    private Mono<ArticleSummary> summarizeSingleDocument(SearchDocument doc) {
        String content = doc.content();
        if (content == null || content.isBlank()) {
            return Mono.just(new ArticleSummary(
                    doc.title(), doc.url(), "No content available",
                    List.of(), EvidenceItem.STANCE_NEUTRAL, 0.3, null));
        }

        String truncated = content.length() > 6000 ? content.substring(0, 6000) + "..." : content;

        String systemPrompt = """
                你是一个专业的研究助手，负责对文章进行结构化摘要。
                请严格按以下 JSON 格式输出（不要添加任何其他文字）：
                {
                  "summary": "文章核心内容摘要（300字以内，用中文）",
                  "keyPoints": ["要点1", "要点2", "要点3"],
                  "stance": "Neutral|Supportive|Critical|Mixed",
                  "confidence": 0.0-1.0,
                  "publishedAt": "发布时间（如2024-01-15、2024年1月、本周、今日、昨日等，无法确定则填null）"
                }
                
                规则：
                - summary: 用中文概括文章核心观点、主要事实和结论，必须包含"谁、做了什么、什么时候、在哪里、为什么"
                - keyPoints: 提取3-5个最关键的信息点，每个要点应包含具体事实而非抽象描述
                - stance: 判断文章立场（Neutral=中立, Supportive=支持性, Critical=批评性, Mixed=混合）
                - confidence: 基于文章来源权威性和内容质量，估算可信度（0.0-1.0）
                - publishedAt: 从文章内容中提取发布时间信息，尽可能准确
                
                【重要约束 - 禁止编造事实】：
                - 只总结输入文章中实际存在的内容，不得补充任何输入中不存在的信息
                - 不得根据常识补全文章中没有的事件、数据、人名、地名
                - 不得根据标题猜测文章正文
                - 如果文章内容不足以确定某个字段，使用 null 或明确说明"信息不足"
                """;

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder().role("system").content(systemPrompt).build());
        messages.add(ChatMessage.builder().role("user")
                .content("标题：" + doc.title() + "\n\n正文：\n" + truncated).build());

        return llmClient.chatWithTools(messages, List.of())
                .map(response -> {
                    String rawContent = response.getContent();
                    if (rawContent == null || rawContent.isBlank()) {
                        return new ArticleSummary(doc.title(), doc.url(), "Empty LLM response",
                                List.of(), EvidenceItem.STANCE_NEUTRAL, 0.3, null);
                    }
                    return parseArticleSummary(rawContent, doc);
                })
                .onErrorReturn(new ArticleSummary(doc.title(), doc.url(), "LLM error",
                        List.of(), EvidenceItem.STANCE_NEUTRAL, 0.3, null));
    }

    private ArticleSummary parseArticleSummary(String rawContent, SearchDocument doc) {
        try {
            String json = extractJson(rawContent);
            Map<String, Object> map = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
            String summary = (String) map.getOrDefault("summary", "Summary unavailable");
            @SuppressWarnings("unchecked")
            List<String> keyPoints = (List<String>) map.getOrDefault("keyPoints", List.of());
            String stance = (String) map.getOrDefault("stance", EvidenceItem.STANCE_NEUTRAL);
            double confidence = map.get("confidence") instanceof Number n
                    ? n.doubleValue() : 0.5;
            String publishedAt = (String) map.getOrDefault("publishedAt", null);
            return new ArticleSummary(doc.title(), doc.url(), summary, keyPoints, stance, confidence, publishedAt);
        } catch (Exception e) {
            log.debug("[ResearchSynthesizer] Failed to parse summary JSON, using raw: {}", e.getMessage());
            return new ArticleSummary(doc.title(), doc.url(),
                    rawContent.length() > 500 ? rawContent.substring(0, 500) : rawContent,
                    List.of(), EvidenceItem.STANCE_NEUTRAL, 0.3, null);
        }
    }

    private String extractJson(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("{");
            int end = trimmed.lastIndexOf("}");
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        int start = trimmed.indexOf("{");
        int end = trimmed.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    /**
     * Stage 2: 构建 Evidence Pool（含来源可信度评级）
     */
    private Mono<EvidencePool> buildEvidencePool(String query, List<SearchResult> searchResults,
                                                  List<SearchDocument> documents,
                                                  List<ArticleSummary> summaries) {
        List<EvidenceItem> items = new ArrayList<>();

        for (int i = 0; i < summaries.size(); i++) {
            ArticleSummary s = summaries.get(i);
            String source = "Unknown";
            String url = s.url();
            if (searchResults != null && i < searchResults.size()) {
                SearchResult sr = searchResults.get(i);
                source = sr.source() != null ? sr.source() : extractDomain(url);
                if (url == null || url.isBlank()) {
                    url = sr.url();
                }
            }
            int sourceRating = SourceCredibility.rate(source);
            items.add(EvidenceItem.of(s.title(), source, url, s.summary(),
                    s.keyPoints(), s.stance(), s.confidence(),
                    s.publishedAt(), sourceRating));
        }

        if (searchResults != null) {
            int docCount = documents != null ? documents.size() : 0;
            for (int i = docCount; i < Math.min(searchResults.size(), docCount + 10); i++) {
                SearchResult sr = searchResults.get(i);
                if (sr.title() != null && !sr.title().isBlank()) {
                    String source = sr.source() != null ? sr.source() : extractDomain(sr.url());
                    int sourceRating = SourceCredibility.rate(source);
                    items.add(EvidenceItem.of(sr.title(),
                            source,
                            sr.url(), sr.snippet() != null ? sr.snippet() : "No snippet",
                            List.of(), EvidenceItem.STANCE_NEUTRAL, sr.score() / 10.0,
                            null, sourceRating));
                }
            }
        }

        log.info("[ResearchSynthesizer] Stage 2: Evidence Pool built with {} items, {} sources",
                items.size(),
                items.stream().map(EvidenceItem::source).distinct().count());
        return Mono.just(EvidencePool.of(query, items));
    }

    /**
     * Stage 3: Cross-Check 交叉验证
     */
    private Mono<EvidencePool> crossCheck(EvidencePool pool) {
        if (pool.evidenceItems().size() < 2) {
            return Mono.just(pool);
        }

        log.info("[ResearchSynthesizer] Stage 3: Cross-checking {} evidence items, consensus={:.0%}",
                pool.evidenceItems().size(), pool.consensusScore());

        boolean hasConflicts = pool.hasConflicts();
        if (hasConflicts) {
            log.info("[ResearchSynthesizer] Cross-check found stance conflicts across sources");
        } else {
            log.info("[ResearchSynthesizer] Cross-check: no significant stance conflicts detected");
        }

        List<EvidenceItem> highCred = pool.highCredibilityEvidence();
        if (!highCred.isEmpty()) {
            log.info("[ResearchSynthesizer] High-credibility sources (4+ stars): {} items from {} sources",
                    highCred.size(),
                    highCred.stream().map(EvidenceItem::source).distinct().count());
        }

        return Mono.just(pool);
    }

    /**
     * Stage 4 (Reduce): 生成最终研究报告（含来源追溯和跨主题综合）
     */
    private Mono<String> generateFinalReport(EvidencePool pool) {
        if (pool.evidenceItems().isEmpty()) {
            return Mono.just("未找到相关信息，无法生成研究报告。");
        }

        log.info("[ResearchSynthesizer] Stage 4 (Reduce): generating final report from {} evidence items",
                pool.evidenceItems().size());

        String evidenceJson = buildEvidenceJson(pool);

        String systemPrompt = """
                你是一个资深的研究分析师，需要基于收集到的证据生成一份结构化的研究报告。
                
                报告必须按以下结构组织（使用 Markdown 格式）：
                
                ## 研究报告：{主题}
                
                ---
                
                ### 一、核心发现
                - 用2-3句话概括最重要的发现，每句话末尾标注来源编号，如【来源1,3】
                - 说明这些发现的时间范围（如"过去48小时""本周""近一月"）
                
                ### 二、主题分析
                
                按主题分别分析，每个主题包含：
                
                #### 主题名称
                - **关键事实**：具体发生了什么（必须标注来源编号）
                - **时间**：事件发生的时间（使用证据中的 publishedAt 字段）
                - **多来源验证**：
                  - 来源一致性：X个来源一致 / 单一来源待验证
                  - 来源列表：Reuters ★★★★★, AP ★★★★★, Guardian ★★★★
                - **影响分析**：这件事可能产生什么影响（如涉及多个来源需分别标注）
                - **不确定性**：哪些信息尚未得到充分证实？
                
                ### 三、观点分歧与争议
                （如果存在不同立场）
                - **支持方认为**：...【来源X,Y】
                - **反对方认为**：...【来源Z】
                - **尚无定论的是**：...
                
                （如果不存在分歧，说明"目前各来源观点基本一致"）
                
                ### 四、跨主题综合
                
                分析不同事件之间的关联：
                - **共同主题**：这些事件共同反映了什么趋势？（如"能源安全""大国竞争""区域联盟"）
                - **因果链**：A事件是否可能导致B事件？（如"如果欧佩克+继续减产 → 油价上涨 → 进口国压力上升"）
                - **关键变量**：未来几周最值得关注的变量是什么？
                
                ### 五、信息来源评级
                
                | 来源 | 可信度 | 引用次数 | 主要贡献 |
                |------|--------|----------|----------|
                | Reuters | ★★★★★ | 3 | 石油价格、OPEC+政策 |
                | CNN | ★★★★ | 2 | 南海局势 |
                | ... | ... | ... | ... |
                
                重要规则：
                - 使用中文撰写
                - 不要按搜索引擎分类（不要写"Google说...Bing说..."）
                - 按主题整合，而非按来源罗列
                - **每条事实性陈述必须标注来源编号**，如：【来源1】【来源2,5】
                - 对不确定的信息要明确说明"证据不足"或"待验证"
                - 不要写"核心发现"和"主要观点"内容重复
                - 核心发现是"结论"，主题分析是"过程"
                - 如果证据不足，明确说"目前仅有X个来源，信息有限，以下分析仅供参考"
                
                【Grounding 约束 - 严格禁止编造】：
                - 所有事实必须来自输入证据池，不得补充输入中不存在的事件、数据、人名、地名
                - 不得生成输入证据中不存在的 URL
                - 不得生成输入证据中不存在的发布日期
                - 不得根据常识补全新闻事件
                - 不得根据标题猜测文章正文
                - 无法从证据确认的信息必须标记为"未知"或"未确认"
                - 系统日期不等于新闻发布日期，不得将当前日期作为事件日期
                """;

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder().role("system").content(systemPrompt).build());
        messages.add(ChatMessage.builder().role("user")
                .content("研究主题：" + pool.query() + "\n\n证据池（每条证据有唯一编号，报告中使用【来源N】引用）：\n" + evidenceJson).build());

        return llmClient.chatWithTools(messages, List.of())
                .map(response -> {
                    String content = response.getContent();
                    log.info("[SearchSynthesis] grounded=true evidencePool={}",
                            pool.evidenceItems().size());
                    return (content != null && !content.isBlank()) ? content : buildFallbackFromPool(pool);
                })
                .onErrorReturn(buildFallbackFromPool(pool));
    }

    private String buildEvidenceJson(EvidencePool pool) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < pool.evidenceItems().size(); i++) {
            EvidenceItem item = pool.evidenceItems().get(i);
            sb.append("  {\n");
            sb.append("    \"来源编号\": ").append(i + 1).append(",\n");
            sb.append("    \"标题\": \"").append(escapeJson(item.title())).append("\",\n");
            sb.append("    \"来源\": \"").append(escapeJson(item.source())).append("\",\n");
            sb.append("    \"来源可信度\": \"").append(item.sourceRatingDisplay()).append("\",\n");
            sb.append("    \"URL\": \"").append(escapeJson(item.url())).append("\",\n");
            if (item.publishedAt() != null) {
                sb.append("    \"发布时间\": \"").append(escapeJson(item.publishedAt())).append("\",\n");
            }
            sb.append("    \"摘要\": \"").append(escapeJson(item.summary())).append("\",\n");
            sb.append("    \"关键点\": [");
            if (item.keyPoints() != null && !item.keyPoints().isEmpty()) {
                sb.append(item.keyPoints().stream()
                        .map(k -> "\"" + escapeJson(k) + "\"")
                        .collect(Collectors.joining(", ")));
            }
            sb.append("],\n");
            sb.append("    \"立场\": \"").append(escapeJson(item.stance())).append("\",\n");
            sb.append("    \"置信度\": ").append(item.confidence()).append("\n");
            sb.append("  }");
            if (i < pool.evidenceItems().size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private Mono<String> buildBasicReport(String query, List<SearchResult> searchResults) {
        return Mono.just(buildFallbackReport(query, searchResults, List.of()));
    }

    private String buildFallbackReport(String query, List<SearchResult> searchResults, List<SearchDocument> documents) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 搜索结果：").append(query).append("\n\n");

        if (searchResults != null && !searchResults.isEmpty()) {
            sb.append("共找到 ").append(searchResults.size()).append(" 条相关结果。\n\n");
            sb.append("### 主要来源\n\n");
            for (int i = 0; i < Math.min(searchResults.size(), 10); i++) {
                SearchResult r = searchResults.get(i);
                sb.append("**").append(i + 1).append(".** [").append(r.title()).append("](").append(r.url()).append(")\n");
                sb.append("  - 来源：").append(r.source() != null ? r.source() : "未知").append("\n");
                if (r.snippet() != null && !r.snippet().isBlank()) {
                    sb.append("  - 摘要：").append(r.snippet()).append("\n");
                }
                sb.append("\n");
            }
        }
        if (documents != null && !documents.isEmpty()) {
            sb.append("### 已抓取网页正文\n\n");
            for (int i = 0; i < documents.size(); i++) {
                SearchDocument doc = documents.get(i);
                sb.append("**").append(i + 1).append(".** ").append(doc.title()).append("\n");
                sb.append("  - URL：").append(doc.url()).append("\n");
                sb.append("  - 内容长度：").append(doc.contentLength()).append(" 字符\n\n");
            }
        }
        return sb.toString();
    }

    private String buildFallbackFromPool(EvidencePool pool) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 研究报告：").append(pool.query()).append("\n\n");
        sb.append("### 收集到的证据（共 ").append(pool.totalSources()).append(" 个来源）\n\n");

        List<EvidenceItem> top = pool.topEvidence();
        for (int i = 0; i < top.size(); i++) {
            EvidenceItem item = top.get(i);
            sb.append("**").append(i + 1).append(".** ").append(item.title()).append("\n");
            sb.append("  - 来源：").append(item.source())
                    .append(" ").append(item.sourceRatingDisplay()).append("\n");
            if (item.publishedAt() != null) {
                sb.append("  - 时间：").append(item.publishedAt()).append("\n");
            }
            sb.append("  - 可信度：").append(String.format("%.2f", item.confidence())).append("\n");
            sb.append("  - 立场：").append(item.stance()).append("\n");
            sb.append("  - 摘要：").append(item.summary()).append("\n");
            if (item.keyPoints() != null && !item.keyPoints().isEmpty()) {
                sb.append("  - 关键点：\n");
                for (String kp : item.keyPoints()) {
                    sb.append("    - ").append(kp).append("\n");
                }
            }
            sb.append("\n");
        }

        if (pool.hasConflicts()) {
            sb.append("### ⚠️ 观点冲突\n\n");
            sb.append("不同来源之间存在立场分歧，详见上方标注。\n\n");
        }

        return sb.toString();
    }

    private String extractDomain(String url) {
        if (url == null || url.isBlank()) return "Unknown";
        try {
            return url.replaceFirst("^https?://", "")
                    .replaceFirst("^www\\.", "")
                    .replaceFirst("/.*$", "");
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * 单篇文章摘要的内部记录（含时间维度）
     */
    private record ArticleSummary(
            String title,
            String url,
            String summary,
            List<String> keyPoints,
            String stance,
            double confidence,
            String publishedAt
    ) {}
}