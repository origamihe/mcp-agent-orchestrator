package com.mcp.engine.sanitizer;

import com.mcp.common.util.ToolBlockStripper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 回复清洗器 — 从 LLM 原始响应中剥离内部工具块，防止 Tool Leakage。
 *
 * <p>设计原则：
 * <ul>
 *   <li>聊天模型只负责生成自然语言回复，Memory/Tool/Workflow 等内部操作不应暴露给用户。</li>
 *   <li>Sanitizer 作为最后一道防线，即使 Prompt 中不慎包含了工具指令，也能兜底清洗。</li>
 *   <li>核心清洗逻辑委托给 {@link com.mcp.common.util.ToolBlockStripper}，确保全模块一致。</li>
 * </ul>
 *
 * <p>与 MemoryLifecycleOrchestrator 的关系：
 * <ul>
 *   <li>MemoryLifecycleOrchestrator 使用独立的 MemoryExtractor（独立 LLM 调用）从对话中抽取记忆。</li>
 *   <li>Sanitizer 只负责清洗聊天模型的输出，两者职责完全解耦。</li>
 *   <li>理想情况下，聊天 Prompt 不应包含任何 Internal_Memory_Storage 指令，
 *       此时 Sanitizer 是零开销的兜底保护。</li>
 * </ul>
 */
@Slf4j
@Component
public class ResponseSanitizer {

    /**
     * 清洗 LLM 原始响应，移除所有内部工具块，返回用户可见的自然语言回复。
     *
     * @param rawResponse LLM 原始响应
     * @return 清洗后的用户可见回复
     */
    public String sanitize(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return rawResponse;
        }

        String cleaned = ToolBlockStripper.strip(rawResponse);

        if (!rawResponse.equals(cleaned)) {
            log.info("[ResponseSanitizer] 清洗了内部工具块，原始长度={} → 清洗后长度={}",
                    rawResponse.length(), cleaned.length());
        }

        return cleaned;
    }
}