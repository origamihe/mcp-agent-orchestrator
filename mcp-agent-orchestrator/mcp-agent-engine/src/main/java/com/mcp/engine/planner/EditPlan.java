package com.mcp.engine.planner;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EditPlan {

    private String intent;
    private PlanType planType;
    private String reasoning;
    private List<PlanStep> steps;
    private int estimatedComplexity;
    private List<String> risks;
    private String testStrategy;

    public enum PlanType {
        CHAT,
        READ_ONLY,
        CODE_EDIT,
        GENERATE,
        MULTI_STEP
    }

    public boolean needsTools() {
        return planType != PlanType.CHAT;
    }

    public boolean needsCodeEdit() {
        return planType == PlanType.CODE_EDIT;
    }

    public boolean isReadOnly() {
        return planType == PlanType.READ_ONLY || planType == PlanType.CHAT;
    }
}