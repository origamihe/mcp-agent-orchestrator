package com.mcp.engine.context;

import com.mcp.core.service.ChatHistoryService;
import com.mcp.core.service.LongTermMemoryService;
import com.mcp.engine.planner.EditPlan;
import com.mcp.engine.planner.PlanStep;
import com.mcp.tools.index.SymbolEntry;
import com.mcp.tools.index.WorkspaceIndex;
import com.mcp.tools.service.WorkspaceFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultContextManager implements ContextManager {

    private final WorkspaceFileService workspaceFileService;
    private final LongTermMemoryService memoryService;
    private final ChatHistoryService chatHistoryService;
    private final WorkspaceIndex workspaceIndex;

    private static final int DEFAULT_MAX_FILE_TOKENS = 4000;
    private static final int DEFAULT_MAX_MEMORY_TOKENS = 1000;
    private static final int DEFAULT_MAX_HISTORY_TOKENS = 1500;
    private static final int MAX_FILE_READ_LINES = 500;

    @Override
    public Mono<ContextBundle> buildContext(EditPlan plan, ContextRequest request) {
        if (plan == null || request == null) {
            return Mono.just(ContextBundle.empty());
        }

        TokenBudget budget = TokenBudget.defaultBudget();
        int maxFileTokens = request.getMaxFileTokens() > 0
                ? request.getMaxFileTokens() : DEFAULT_MAX_FILE_TOKENS;
        int maxMemoryTokens = request.getMaxMemoryTokens() > 0
                ? request.getMaxMemoryTokens() : DEFAULT_MAX_MEMORY_TOKENS;
        int maxHistoryTokens = request.getMaxHistoryTokens() > 0
                ? request.getMaxHistoryTokens() : DEFAULT_MAX_HISTORY_TOKENS;

        Mono<List<FileContext>> fileContextsMono = loadFileContexts(plan, request, budget, maxFileTokens);
        Mono<String> memoryContextMono = loadMemoryContext(request, budget, maxMemoryTokens);
        Mono<String> historyContextMono = loadHistoryContext(request, budget, maxHistoryTokens);

        return Mono.zip(fileContextsMono, memoryContextMono, historyContextMono)
                .map(tuple -> {
                    List<FileContext> fileContexts = tuple.getT1();
                    String memoryContext = tuple.getT2();
                    String historyContext = tuple.getT3();

                    budget.setFileContextTokens(
                            fileContexts.stream().mapToInt(FileContext::getEstimatedTokens).sum());
                    budget.setMemoryTokens(TokenBudget.estimateTokens(memoryContext));
                    budget.setHistoryTokens(TokenBudget.estimateTokens(historyContext));

                    log.info("[ContextManager] Context built: files={} ({} tokens), memory={} tokens, history={} tokens, remaining={} tokens",
                            fileContexts.size(), budget.getFileContextTokens(),
                            budget.getMemoryTokens(), budget.getHistoryTokens(),
                            budget.remaining());

                    return ContextBundle.builder()
                            .fileContexts(fileContexts)
                            .memoryContext(memoryContext)
                            .historyContext(historyContext)
                            .budget(budget)
                            .build();
                });
    }

    private Mono<List<FileContext>> loadFileContexts(EditPlan plan, ContextRequest request,
                                                     TokenBudget budget, int maxTokens) {
        Set<String> filesToRead = new LinkedHashSet<>();

        for (PlanStep step : plan.getSteps()) {
            if (step.getToolName() != null && step.getArguments() != null) {
                String path = (String) step.getArguments().getOrDefault("path",
                        step.getArguments().get("filePath"));
                if (path != null && !path.isBlank()) {
                    filesToRead.add(path);
                }
            }
        }

        if (request.getFilePaths() != null) {
            filesToRead.addAll(request.getFilePaths());
        }

        if (filesToRead.isEmpty() && workspaceIndex.isInitialized()) {
            String keyword = extractKeywordFromRequest(request.getUserRequest());
            if (keyword != null) {
                for (SymbolEntry s : workspaceIndex.searchSymbol(keyword).stream().limit(5).toList()) {
                    filesToRead.add(s.getFilePath());
                }
            }
        }

        if (filesToRead.isEmpty()) {
            return Mono.just(List.of());
        }

        List<Mono<FileContext>> fileMonos = filesToRead.stream()
                .map(fp -> readFileContext(fp, maxTokens / Math.max(1, filesToRead.size())))
                .collect(Collectors.toList());

        return Mono.zip(fileMonos, objects -> {
            List<FileContext> result = new ArrayList<>();
            int tokenUsed = 0;
            for (Object obj : objects) {
                FileContext fc = (FileContext) obj;
                if (fc != null && tokenUsed + fc.getEstimatedTokens() <= maxTokens) {
                    result.add(fc);
                    tokenUsed += fc.getEstimatedTokens();
                }
            }
            return result;
        });
    }

    private Mono<FileContext> readFileContext(String filePath, int maxTokens) {
        return Mono.fromCallable(() -> {
            try {
                Path resolved = workspaceFileService.resolve(filePath);
                if (!Files.exists(resolved) || Files.isDirectory(resolved)) {
                    return null;
                }

                List<String> allLines = Files.readAllLines(resolved);
                int totalLines = allLines.size();

                int linesToRead = Math.min(totalLines, MAX_FILE_READ_LINES);

                String content;
                boolean isFullFile;
                if (linesToRead >= totalLines) {
                    content = String.join("\n", allLines);
                    isFullFile = true;
                } else {
                    content = String.join("\n", allLines.subList(0, linesToRead));
                    isFullFile = false;
                }

                int estimatedTokens = TokenBudget.estimateTokens(content);
                if (estimatedTokens > maxTokens) {
                    int targetLines = (int) ((long) linesToRead * maxTokens / estimatedTokens);
                    targetLines = Math.max(10, targetLines);
                    content = String.join("\n", allLines.subList(0, Math.min(targetLines, totalLines)));
                    isFullFile = false;
                    estimatedTokens = TokenBudget.estimateTokens(content);
                }

                return FileContext.builder()
                        .filePath(filePath)
                        .content(content)
                        .startLine(1)
                        .endLine(isFullFile ? totalLines : linesToRead)
                        .estimatedTokens(estimatedTokens)
                        .isFullFile(isFullFile)
                        .build();
            } catch (Exception e) {
                log.warn("[ContextManager] Failed to read file: {} - {}", filePath, e.getMessage());
                return null;
            }
        });
    }

    private Mono<String> loadMemoryContext(ContextRequest request, TokenBudget budget, int maxTokens) {
        if (request.getSessionId() == null) {
            return Mono.just("");
        }
        return memoryService.buildWorkingContext(request.getSessionId())
                .map(memory -> truncateByTokens(memory, maxTokens))
                .defaultIfEmpty("")
                .doOnNext(m -> log.debug("[ContextManager] Memory context: {} chars", m.length()));
    }

    private Mono<String> loadHistoryContext(ContextRequest request, TokenBudget budget, int maxTokens) {
        if (request.getSessionId() == null) {
            return Mono.just("");
        }
        return chatHistoryService.getRecentMessages(request.getSessionId(), 20)
                .map(messages -> {
                    if (messages == null || messages.isEmpty()) {
                        return "";
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append("最近对话（").append(messages.size()).append("条）：\n");
                    for (var msg : messages) {
                        String role = "user".equals(msg.getRole()) ? "用户" : "助手";
                        String content = msg.getContent();
                        if (content != null && content.length() > 200) {
                            content = content.substring(0, 200) + "...";
                        }
                        sb.append(role).append(": ").append(content).append("\n");
                    }
                    return truncateByTokens(sb.toString(), maxTokens);
                })
                .defaultIfEmpty("")
                .doOnNext(h -> log.debug("[ContextManager] History context: {} chars", h.length()));
    }

    private String truncateByTokens(String text, int maxTokens) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int estimatedTokens = TokenBudget.estimateTokens(text);
        if (estimatedTokens <= maxTokens) {
            return text;
        }
        int targetChars = maxTokens * 4;
        if (targetChars >= text.length()) {
            return text;
        }
        return text.substring(0, targetChars) + "\n...（内容已截断，超出 Token 预算）";
    }

    private String extractKeywordFromRequest(String userRequest) {
        if (userRequest == null || userRequest.isBlank()) {
            return null;
        }
        String[] words = userRequest.split("[\\s，。！？、]+");
        for (String word : words) {
            if (word.length() >= 3 && Character.isUpperCase(word.charAt(0))) {
                return word;
            }
        }
        return words.length > 0 ? words[words.length - 1] : null;
    }
}