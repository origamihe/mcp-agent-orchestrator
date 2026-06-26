package com.mcp.tools.security;

import com.mcp.common.identity.CommandLevel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具权限注解 - 标注在 MCP Tool 方法上
 * 程序负责权限，模型负责聊天
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    CommandLevel value() default CommandLevel.USER;
    String description() default "";
}