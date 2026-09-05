package com.mcp.tools.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档生成器工厂 — 自动发现所有 {@link DocumentGenerator} Bean，按格式路由到对应生成器。
 *
 * 职责：
 * 1. 自动发现所有 DocumentGenerator Spring Bean
 * 2. getGenerator(format) 按格式返回对应生成器
 * 3. 新增文档类型只需实现 DocumentGenerator 接口并注册为 Spring Bean
 *
 * 设计原则：
 * - 不硬编码格式列表，通过 supports() 方法动态匹配
 * - 生成器不存在时抛出异常，而非静默返回 null
 */
@Slf4j
@Component
public class DocumentGeneratorFactory {

    private final List<DocumentGenerator> generators;

    public DocumentGeneratorFactory(List<DocumentGenerator> generators) {
        this.generators = generators;
        log.info("[DocumentGeneratorFactory] Registered {} document generators: {}",
                generators.size(),
                generators.stream()
                        .map(g -> g.getClass().getSimpleName())
                        .collect(Collectors.joining(", ")));
    }

    /**
     * 根据格式获取对应的文档生成器。
     *
     * @param format 文档格式（pdf / xlsx / excel / html 等）
     * @return 对应的 DocumentGenerator 实现
     * @throws IllegalArgumentException 如果没有生成器支持该格式
     */
    public DocumentGenerator getGenerator(String format) {
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("Document format must not be null or blank");
        }

        for (DocumentGenerator generator : generators) {
            if (generator.supports(format)) {
                log.debug("[DocumentGeneratorFactory] Resolved generator for format '{}': {}",
                        format, generator.getClass().getSimpleName());
                return generator;
            }
        }

        String supportedFormats = generators.stream()
                .flatMap(g -> {
                    java.util.List<String> formats = new java.util.ArrayList<>();
                    for (String f : new String[]{"pdf", "xlsx", "excel", "html"}) {
                        if (g.supports(f)) {
                            formats.add(f);
                        }
                    }
                    return formats.stream();
                })
                .distinct()
                .collect(Collectors.joining(", "));

        throw new IllegalArgumentException(
                "No DocumentGenerator found for format '" + format
                + "'. Supported formats: [" + supportedFormats + "]");
    }

    /**
     * 获取所有已注册的文档生成器。
     */
    public List<DocumentGenerator> getAllGenerators() {
        return List.copyOf(generators);
    }
}