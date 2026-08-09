package com.mcp.core.context;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 上下文渲染器 — 将 PromptLayer 列表渲染为最终 System Prompt 字符串。
 *
 * 职责：
 * 1. 按 PromptLayer.priority() 排序
 * 2. 依次调用每个 Layer 的 render() 方法
 * 3. 跳过空内容
 * 4. 不做任何决策、条件判断、业务逻辑
 *
 * 设计原则：
 * - Assembler 只知道 PromptLayer 接口，不知道 Persona、Workspace、Memory 等具体层
 * - 所有业务决策（哪些 Layer 参与、什么顺序）由 PromptPolicy 和 PromptLayer.priority() 决定
 * - 新增 Layer 类型无需修改 Assembler
 * - 位于 mcp-core，不依赖 Engine 或 Gateway
 */
@Component
public class ContextAssembler {

    /**
     * 将 PromptLayer 列表渲染为 System Prompt 字符串。
     * 按 priority 升序排列，自动跳过空内容。
     *
     * @param layers PromptLayer 列表
     * @return 渲染后的 System Prompt 字符串
     */
    public String render(List<PromptLayer> layers) {
        if (layers == null || layers.isEmpty()) {
            return "";
        }

        return layers.stream()
                .sorted(Comparator.comparingInt(PromptLayer::priority))
                .map(PromptLayer::render)
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.joining("\n\n"));
    }
}