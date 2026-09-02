package com.mcp.tools.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 文档生成器工厂 — 根据格式路由到对应的 DocumentGenerator 实现。
 *
 * 自动发现所有实现了 DocumentGenerator 接口的 Spring Bean，
 * 通过 supports(format) 方法匹配对应生成器。
 *
 * 使用方式：
 *   DocumentGenerator gen = factory.getGenerator("pdf");
 *   DocumentGenerator.DocResult result = gen.generate(llmJson, title);
 */
@Slf4j
@Component
public class DocumentGeneratorFactory {

    private final List<DocumentGenerator> generators;

    public DocumentGeneratorFactory(List<DocumentGenerator> generatorList) {
        this.generators = new ArrayList<>(generatorList);
        log.info("[DocGenFactory] Registered {} document generators: {}",
                generators.size(),
                generatorList.stream().map(g -> g.getClass().getSimpleName()).toList());
    }

    public DocumentGenerator getGenerator(String format) {
        for (DocumentGenerator gen : generators) {
            if (gen.supports(format)) {
                return gen;
            }
        }
        throw new IllegalArgumentException("Unsupported document format: " + format
                + ". Available: " + getSupportedFormats());
    }

    public List<String> getSupportedFormats() {
        Set<String> formats = new LinkedHashSet<>();
        for (DocumentGenerator gen : generators) {
            try {
                String format = gen.getClass().getSimpleName()
                        .replace("GeneratorTool", "")
                        .toLowerCase();
                if (!format.isEmpty()) {
                    formats.add(format);
                }
            } catch (Exception e) {
                log.warn("[DocGenFactory] Failed to resolve format for {}", gen.getClass().getSimpleName(), e);
            }
        }
        return List.copyOf(formats);
    }

    public List<DocumentGenerator> getAllGenerators() {
        return List.copyOf(generators);
    }
}