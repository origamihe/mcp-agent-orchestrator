package com.mcp.engine.context;

import com.mcp.engine.planner.EditPlan;
import reactor.core.publisher.Mono;

public interface ContextManager {

    Mono<ContextBundle> buildContext(EditPlan plan, ContextRequest request);
}