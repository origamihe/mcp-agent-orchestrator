package com.mcp.gateway.controller;

import com.mcp.core.domain.prompt.PromptTemplate;
import com.mcp.core.domain.prompt.PromptType;
import com.mcp.core.service.PromptService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/mcp/prompts")
public class PromptController {

    private final PromptService promptService;

    public PromptController(PromptService promptService) {
        this.promptService = promptService;
    }

    @GetMapping
    public Mono<?> listPrompts() {
        return promptService.getAllActivePrompts()
                .map(prompts -> prompts.stream()
                        .map(p -> Map.of(
                                "name", p.getName(),
                                "type", p.getType().getCode(),
                                "templateText", p.getTemplateText(),
                                "description", p.getDescription() != null ? p.getDescription() : "",
                                "version", p.getVersion(),
                                "updatedAt", p.getUpdatedAt().toString()
                        ))
                        .toList());
    }

    @PostMapping
    public Mono<Map<String, String>> createPrompt(@RequestBody Map<String, String> body) {
        return Mono.fromCallable(() -> {
            String name = body.get("name");
            String typeStr = body.getOrDefault("type", "AGENT_SPECIFIC");
            String templateText = body.get("templateText");
            String description = body.getOrDefault("description", "");

            if (name == null || name.trim().isEmpty()) throw new RuntimeException("name is required");
            if (templateText == null || templateText.trim().isEmpty()) throw new RuntimeException("templateText is required");

            PromptType type = PromptType.valueOf(typeStr);
            int nextVersion = 1;

            PromptTemplate template = new PromptTemplate(
                    name.trim(),
                    "default",
                    type,
                    templateText.trim(),
                    description.trim(),
                    nextVersion,
                    1.0,
                    true,
                    LocalDateTime.now()
            );
            promptService.savePrompt(template);
            return Map.of("status", "ok", "name", name);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{name}")
    public Mono<Map<String, String>> deletePrompt(@PathVariable String name) {
        return Mono.fromCallable(() -> {
            promptService.deletePrompt(name);
            return Map.of("status", "ok", "name", name);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}