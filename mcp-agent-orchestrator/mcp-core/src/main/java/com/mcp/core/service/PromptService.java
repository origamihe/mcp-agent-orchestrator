package com.mcp.core.service;

import com.mcp.common.util.ToolBlockStripper;
import com.mcp.core.domain.prompt.PromptTemplate;
import com.mcp.core.domain.prompt.PromptType;
import com.mcp.core.entity.PromptTemplateEntity;
import com.mcp.core.mapper.PromptTemplateMapper;
import com.mcp.core.repository.PromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Prompt 模板领域服务
 */
@Service
@RequiredArgsConstructor
public class PromptService {

    private final PromptTemplateRepository repository;
    private final PromptTemplateMapper mapper;

    /**
     * 获取指定名称的 Prompt 模板（缓存）
     */
    @Cacheable(value = "prompts", key = "#name")
    public Mono<PromptTemplate> getPrompt(String name) {
        return Mono.fromCallable(() -> repository.findByName(name)
                .map(mapper::toDomain)
                .orElseThrow(() -> new RuntimeException("Prompt template not found: " + name)));
    }

    /**
     * 根据类型获取最新 Prompt（缓存）
     */
    @Cacheable(value = "prompts", key = "'type_' + #type.name()")
    public Mono<PromptTemplate> getLatestPromptByType(PromptType type) {
        return Mono.fromCallable(() -> repository.findLatestByType(type).stream()
                .findFirst()
                .map(mapper::toDomain)
                .orElseThrow(() -> new RuntimeException("No prompt found for type: " + type)));
    }

    /**
     * 渲染 Prompt 模板（支持变量替换）
     */
    public Mono<String> renderPrompt(String name, Map<String, Object> variables) {
        return getPrompt(name)
                .map(template -> template.render(variables));
    }

    /**
     * 获取核心系统 Prompt（带默认值，缓存）。
     * 加载时自动清洗 [Internal_Memory_Storage] 指令，防止 Tool Leakage。
     * 记忆抽取已由后台 MemoryLifecycleOrchestrator 独立处理。
     */
    @Cacheable(value = "prompts", key = "'core_system'")
    public Mono<String> getCoreSystemPrompt() {
        return getPrompt("core_system")
                .map(PromptTemplate::getTemplateText)
                .map(ToolBlockStripper::strip)
                .onErrorReturn("""
                    你是一个专业、友好、高效的 AI Agent 助手。
                    请清晰、结构化地回答用户问题，必要时使用工具。
                    """);
    }

    /**
     * 保存或更新 Prompt 模板（清除缓存）
     */
    @CacheEvict(value = "prompts", key = "#template.name")
    public void savePrompt(PromptTemplate template) {
        PromptTemplateEntity entity = mapper.toEntity(template);
        repository.save(entity);
    }

    /**
     * 获取所有活跃的 Prompt 模板
     */
    public Mono<java.util.List<PromptTemplate>> getAllActivePrompts() {
        return Mono.fromCallable(() -> repository.findAllActiveTemplates().stream()
                .map(mapper::toDomain)
                .collect(java.util.stream.Collectors.toList()));
    }

    /**
     * 删除指定名称的 Prompt 模板（清除缓存）
     */
    @CacheEvict(value = "prompts", key = "#name")
    public void deletePrompt(String name) {
        repository.deleteById(name);
    }
}