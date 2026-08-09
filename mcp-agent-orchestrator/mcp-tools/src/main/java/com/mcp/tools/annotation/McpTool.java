package com.mcp.tools.annotation;

import com.mcp.tools.model.ToolCapability;
import com.mcp.tools.model.ToolCategory;
import com.mcp.tools.model.ToolOwner;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {

    String name() default "";           // tool name, default to method name
    String description() default "";    // tool description, LLM will see this

    String[] tags() default {};         // legacy tag-based classification

    // P7: enhanced fields
    ToolCategory category() default ToolCategory.CUSTOM;  // tool category
    ToolCapability[] capabilities() default {ToolCapability.CUSTOM};  // capability semantics (multi-valued)
    ToolOwner owner() default ToolOwner.SYSTEM;           // architectural ownership
    long timeoutMs() default 30000;     // execution timeout, default 30s
    String[] examples() default {};     // usage examples
    int priority() default 0;           // execution priority
}