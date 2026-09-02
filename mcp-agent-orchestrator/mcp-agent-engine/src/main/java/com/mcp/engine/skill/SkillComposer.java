package com.mcp.engine.skill;

import com.mcp.core.domain.memory.SkillEntity;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能组合器 — 将现有技能组合为可复用的工作流模板。
 *
 * 核心理念：
 * - 能力可组合（Capability Composition）而非 JAR 热加载
 * - 已有技能（web_search、deep_research、synthesize、format_output）可组合成工作流
 * - 工作流是"技能的技能"，可以被检索、版本化、进化
 *
 * 预置工作流模板：
 * <pre>
 * SEARCH_AND_SYNTHESIZE → web_search → deep_research → synthesize
 * FACT_CHECK            → web_search → cross_verify → summarize
 * DOCUMENT_GENERATE     → research → outline → draft → review → finalize
 * DAILY_BRIEFING        → fetch_news → categorize → summarize → format_email
 * </pre>
 */
@Slf4j
public class SkillComposer {

    private final Map<String, SkillPipeline> templates = new ConcurrentHashMap<>();

    public SkillComposer() {
        registerDefaultTemplates();
    }

    private void registerDefaultTemplates() {
        register("SEARCH_AND_SYNTHESIZE",
                SkillPipeline.startWith("web_search")
                        .named("SEARCH_AND_SYNTHESIZE")
                        .then("deep_research")
                        .then("synthesize")
                        .build());

        register("FACT_CHECK",
                SkillPipeline.startWith("web_search")
                        .named("FACT_CHECK")
                        .then("cross_verify")
                        .then("summarize")
                        .build());

        register("DOCUMENT_GENERATE",
                SkillPipeline.startWith("research")
                        .named("DOCUMENT_GENERATE")
                        .then("outline")
                        .then("draft")
                        .then("review")
                        .then("finalize")
                        .build());

        register("DAILY_BRIEFING",
                SkillPipeline.startWith("fetch_news")
                        .named("DAILY_BRIEFING")
                        .then("categorize")
                        .then("summarize")
                        .then("format_email")
                        .build());
    }

    /**
     * 注册工作流模板。
     */
    public void register(String name, SkillPipeline pipeline) {
        templates.put(name, pipeline);
        log.info("[SkillComposer] Registered template: {}", name);
    }

    /**
     * 获取工作流模板。
     */
    public SkillPipeline getTemplate(String name) {
        SkillPipeline template = templates.get(name);
        if (template == null) {
            throw new IllegalArgumentException("Unknown template: " + name);
        }
        return template;
    }

    /**
     * 根据用户意图匹配工作流模板。
     */
    public List<MatchedTemplate> match(String userIntent) {
        List<MatchedTemplate> matches = new ArrayList<>();

        for (var entry : templates.entrySet()) {
            String name = entry.getKey();
            SkillPipeline pipeline = entry.getValue();

            double score = computeMatchScore(name, userIntent);
            if (score > 0) {
                matches.add(new MatchedTemplate(name, pipeline, score));
            }
        }

        matches.sort((a, b) -> Double.compare(b.score(), a.score()));
        return matches;
    }

    /**
     * 获取所有已注册模板。
     */
    public List<String> listTemplates() {
        return List.copyOf(templates.keySet());
    }

    /**
     * 从技能实体列表中构建管道。
     */
    public SkillPipeline composeFromSkills(String name, List<SkillEntity> skills) {
        if (skills.isEmpty()) {
            throw new IllegalArgumentException("Cannot compose pipeline from empty skill list");
        }

        SkillPipeline.PipelineBuilder builder = SkillPipeline.startWith(skills.get(0).getName())
                .named(name);

        for (int i = 1; i < skills.size(); i++) {
            builder.then(skills.get(i).getName());
        }

        return builder.build();
    }

    private double computeMatchScore(String templateName, String userIntent) {
        String lowerName = templateName.toLowerCase();
        String lowerIntent = userIntent.toLowerCase();

        double score = 0;

        if (lowerIntent.contains("日报") || lowerIntent.contains("简报") || lowerIntent.contains("daily")
                || lowerIntent.contains("briefing") || lowerIntent.contains("今日")) {
            if (lowerName.contains("daily") || lowerName.contains("briefing")) score += 0.95;
        }
        if (lowerIntent.contains("搜索") || lowerIntent.contains("查询") || lowerIntent.contains("search")) {
            if (lowerName.contains("search")) score += 0.8;
            if (lowerName.contains("fact")) score += 0.5;
        }
        if (lowerIntent.contains("事实") || lowerIntent.contains("验证") || lowerIntent.contains("check")) {
            if (lowerName.contains("fact")) score += 0.9;
            if (lowerName.contains("verify")) score += 0.7;
        }
        if (lowerIntent.contains("文档") || lowerIntent.contains("doc") || lowerIntent.contains("生成")
                || lowerIntent.contains("报告")) {
            if (lowerName.contains("document") || lowerName.contains("generate")) score += 0.9;
        }
        if (lowerIntent.contains("综合") || lowerIntent.contains("分析") || lowerIntent.contains("synthesize")) {
            if (lowerName.contains("synthesize")) score += 0.7;
        }

        return Math.min(score, 1.0);
    }

    /**
     * 匹配到的模板。
     */
    public record MatchedTemplate(String name, SkillPipeline pipeline, double score) {}
}