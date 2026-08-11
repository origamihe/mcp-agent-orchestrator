package com.mcp.tools.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    private final Map<String, DocumentGenerator> generators;

    public DocumentGeneratorFactory(List<DocumentGenerator> generatorList) {
        this.generators = generatorList.stream()
                .collect(Collectors.toMap(
                        gen -> gen.getClass().getSimpleName(),
                        gen -> gen,
                        (a, b) -> a
                ));
        log.info("[DocGenFactory] Registered {} document generators: {}",
                generators.size(),
                generatorList.stream().map(g -> g.getClass().getSimpleName()).toList());
    }

    public DocumentGenerator getGenerator(String format) {
        for (DocumentGenerator gen : generators.values()) {
            if (gen.supports(format)) {
                return gen;
            }
        }
        throw new IllegalArgumentException("Unsupported document format: " + format
                + ". Available: " + getSupportedFormats());
    }

    public List<String> getSupportedFormats() {
        return generators.values().stream()
                .flatMap(gen -> {
                    try {
                        return java.util.stream.Stream.of(
                                gen.getClass().getSimpleName().replace("GeneratorTool", "").toLowerCase());
                    } catch (Exception e) {
                        return java.util.stream.Stream.empty();
                    }
                })
                .toList();
    }

    public List<DocumentGenerator> getAllGenerators() {
        return List.copyOf(generators.values());
    }
}