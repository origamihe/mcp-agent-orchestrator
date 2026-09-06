package com.mcp.core.context.provider;

import com.mcp.core.context.BuildContext;
import com.mcp.core.context.ContextProvider;
import com.mcp.core.context.PromptContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 日期上下文提供者 — 填充日期层（dateContext）。
 *
 * 为 LLM 提供明确的当前日期、时间和时区信息，
 * 使 LLM 能正确理解"今天"、"最近"、"当前"、"实时"等时间相关请求。
 *
 * 设计原则：
 * - 所有 Agent 共享此 Provider，不各自调用 LocalDateTime.now()
 * - 通过 ContextProvider 统一注入，遵循 BuildContext → PromptContext 架构
 * - 日期格式明确，禁止只告诉模型"今天"
 */
@Component
public class DateContextProvider implements ContextProvider {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy年M月d日").withZone(ZONE);

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZONE);

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZONE);

    @Override
    public void collect(PromptContext.PromptContextBuilder builder, BuildContext ctx) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        String dateContext = String.format("""
                【当前日期时间】
                当前日期：%s
                当前时间：%s
                完整日期时间：%s
                时区：%s""",
                DATE_FORMATTER.format(now),
                TIME_FORMATTER.format(now),
                DATETIME_FORMATTER.format(now),
                ZONE.getId());
        builder.dateContext(dateContext);
    }
}