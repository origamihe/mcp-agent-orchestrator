package com.mcp.engine.context;

import com.mcp.engine.planner.EditPlan;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenBudget {

    private static final int DEFAULT_TOTAL_BUDGET = 128000;
    private static final int LEGACY_DEFAULT = 8000;

    private static final int SMALL_MODEL_THRESHOLD = 32000;
    private static final int MEDIUM_MODEL_THRESHOLD = 64000;

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
                .totalBudget(LEGACY_DEFAULT)
                .systemPromptTokens(0)
                .fileContextTokens(0)
                .memoryTokens(0)
                .historyTokens(0)
                .toolResultTokens(0)
                .build();
    }

    public static TokenBudget forPlanType(EditPlan.PlanType planType) {
        return forPlanType(planType, DEFAULT_TOTAL_BUDGET);
    }

    public static TokenBudget forPlanType(EditPlan.PlanType planType, int totalBudget) {
        return createBudget(planType, totalBudget);
    }

    public static TokenBudget forModel(EditPlan.PlanType planType, int modelContextWindow) {
        int effectiveBudget = clampBudget(modelContextWindow);
        return createBudget(planType, effectiveBudget);
    }

    private static int clampBudget(int modelContextWindow) {
        if (modelContextWindow <= 0) {
            return LEGACY_DEFAULT;
        }
        if (modelContextWindow <= SMALL_MODEL_THRESHOLD) {
            return Math.min(modelContextWindow, 8192);
        }
        if (modelContextWindow <= MEDIUM_MODEL_THRESHOLD) {
            return Math.min(modelContextWindow, 32768);
        }
        return Math.min(modelContextWindow, DEFAULT_TOTAL_BUDGET);
    }

    private static TokenBudget createBudget(EditPlan.PlanType planType, int totalBudget) {
        return switch (planType) {
            case CHAT -> TokenBudget.builder()
                    .totalBudget(totalBudget)
                    .systemPromptTokens((int) (totalBudget * 0.20))
                    .fileContextTokens(0)
                    .memoryTokens((int) (totalBudget * 0.10))
                    .historyTokens((int) (totalBudget * 0.35))
                    .toolResultTokens((int) (totalBudget * 0.25))
                    .build();

            case READ_ONLY -> TokenBudget.builder()
                    .totalBudget(totalBudget)
                    .systemPromptTokens((int) (totalBudget * 0.15))
                    .fileContextTokens((int) (totalBudget * 0.40))
                    .memoryTokens((int) (totalBudget * 0.05))
                    .historyTokens((int) (totalBudget * 0.10))
                    .toolResultTokens((int) (totalBudget * 0.30))
                    .build();

            case CODE_EDIT -> TokenBudget.builder()
                    .totalBudget(totalBudget)
                    .systemPromptTokens((int) (totalBudget * 0.15))
                    .fileContextTokens((int) (totalBudget * 0.30))
                    .memoryTokens((int) (totalBudget * 0.05))
                    .historyTokens((int) (totalBudget * 0.10))
                    .toolResultTokens((int) (totalBudget * 0.40))
                    .build();

            case GENERATE -> TokenBudget.builder()
                    .totalBudget(totalBudget)
                    .systemPromptTokens((int) (totalBudget * 0.15))
                    .fileContextTokens((int) (totalBudget * 0.10))
                    .memoryTokens((int) (totalBudget * 0.05))
                    .historyTokens((int) (totalBudget * 0.10))
                    .toolResultTokens((int) (totalBudget * 0.60))
                    .build();

            case MULTI_STEP -> TokenBudget.builder()
                    .totalBudget(totalBudget)
                    .systemPromptTokens((int) (totalBudget * 0.10))
                    .fileContextTokens((int) (totalBudget * 0.25))
                    .memoryTokens((int) (totalBudget * 0.05))
                    .historyTokens((int) (totalBudget * 0.10))
                    .toolResultTokens((int) (totalBudget * 0.50))
                    .build();
        };
    }

    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int charCount = text.length();
        int chineseChars = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.UnicodeBlock.of(text.charAt(i)) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                chineseChars++;
            }
        }
        int nonChineseChars = charCount - chineseChars;
        return chineseChars + (nonChineseChars / 4);
    }
}