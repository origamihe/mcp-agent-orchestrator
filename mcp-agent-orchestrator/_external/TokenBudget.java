package com.mcp.engine.context;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenBudget{

    private int totalBudget;
    private int systemPromptTokens;
    private int fileContextTokens;
    private int memoryTokens;
    private int historyTokens;
    private int toolResultTokens;

    public int remaining() {
        int used = systemPromptTokens + fileContextTokens + memoryTokens
                + historyTokens + toolResultTokens;
        return Math.max(0, totalBudget - used);
    }

    public boolean canFit(int additionalTokens) {
        return remaining() >= additionalTokens;
    }

    public static TokenBudget defaultBudget() {
        return TokenBudget.builder()
                .totalBudget(8000)
                .systemPromptTokens(0)
                .fileContextTokens(0)
                .memoryTokens(0)
                .historyTokens(0)
                .toolResultTokens(0)
                .build();
    }

    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() / 4;
    }
}