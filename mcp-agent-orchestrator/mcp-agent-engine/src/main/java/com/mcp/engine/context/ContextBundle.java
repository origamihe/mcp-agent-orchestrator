package com.mcp.engine.context;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class ContextBundle {

    private List<FileContext> fileContexts;
    private String memoryContext;
    private String historyContext;
    private ContextBudget budget;

    public String buildPrompt() {
        StringBuilder sb = new StringBuilder(4096);

        if (fileContexts != null && !fileContexts.isEmpty()) {
            sb.append("【相关代码上下文】\n");
            for (FileContext fc : fileContexts) {
                sb.append(fc.toPromptBlock()).append("\n");
            }
        }

        if (memoryContext != null && !memoryContext.isBlank()) {
            sb.append("【相关记忆】\n").append(memoryContext).append("\n\n");
        }

        if (historyContext != null && !historyContext.isBlank()) {
            sb.append("【对话历史摘要】\n").append(historyContext).append("\n\n");
        }

        return sb.toString().trim();
    }

    public int totalTokens() {
        return budget == null ? 0 : budget.maxTokens();
    }

    public static ContextBundle empty() {
        return ContextBundle.builder()
                .fileContexts(new ArrayList<>())
                .memoryContext("")
                .historyContext("")
                .budget(ContextBudget.DEFAULT)
                .build();
    }
}