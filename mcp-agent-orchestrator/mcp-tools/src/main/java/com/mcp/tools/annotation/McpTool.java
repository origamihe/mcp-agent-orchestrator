package com.mcp.tools.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {

    String name() default "";           // 工具名称（默认使用方法名）
    String description() default "";    // 工具描述（非常重要，LLM 会看到）

    String[] tags() default {};         // 标签分类
}