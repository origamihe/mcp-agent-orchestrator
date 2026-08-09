package com.mcp.core.context;

import com.mcp.core.context.BuildContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 上下文构建器 — 调度所有 ContextProvider，将原始数据组装为 PromptContext。
 *
 * 职责：
 * 1. 接收 BuildContext 统一上下文对象
 * 2. 并行调度所有 ContextProvider，填充 PromptContext 的每一层
 * 3. 返回完整的 PromptContext 或 PromptLayer 列表
 *
 * 设计原则：
 * - 构建器本身不包含任何业务逻辑，只负责调度所有 ContextProvider，将数据组装为 PromptContext
 * - 每个 Provider 独立负责自己的数据层
 * - 新增 Provider 时，只需实现 ContextProvider 并注册为 Spring Bean，无需修改本类
 * - 位于 mcp-core，不依赖 Engine 或 Gateway，保持核心功能的独立性和可测试性
 * - 方法签名统一使用 BuildContext 作为唯一参数，避免参数管道膨胀
 * - P3 优化：Provider 并行执行，各 Provider 间无依赖关系，可安全并行
 */
@Component
public class PromptContextBuilder {

    private final List<ContextProvider> providers;

    public PromptContextBuilder(List<ContextProvider> providers) {
        this.providers = providers;
    }

    /**
     * 从 BuildContext 构建 PromptContext。
     * 并行调用所有 ContextProvider，填充各层数据（包括 Workspace、HostContext）。
     * 每个 Provider 写入不同字段，通过 synchronized 保证 builder 线程安全。
     */
    public PromptContext build(BuildContext ctx) {
        PromptContext.PromptContextBuilder builder = PromptContext.builder()
                .baseSystemPrompt(ctx.baseSystemPrompt());

        if (providers.size() <= 1) {
            for (ContextProvider provider : providers) {
                provider.collect(builder, ctx);
            }
        } else {
            List<CompletableFuture<Void>> futures = providers.stream()
                    .map(p -> CompletableFuture.runAsync(() -> {
                        synchronized (builder) {
                            p.collect(builder, ctx);
                        }
                    }))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        return builder.build();
    }

    /**
     * 构建并返回 PromptLayer 列表。
     * 内部复用 build() 构建 PromptContext，然后通过 toLayers() 转换为 Layer 列表。
     */
    public List<PromptLayer> buildLayers(BuildContext ctx) {
        return build(ctx).toLayers();
    }
}