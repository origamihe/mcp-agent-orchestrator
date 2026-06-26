package com.mcp.engine.planner;

import reactor.core.publisher.Mono;

public interface Planner {

    Mono<EditPlan> plan(String userRequest, PlanContext context);
}