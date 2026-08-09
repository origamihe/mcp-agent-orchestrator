package com.mcp.core.context;

/**
 * Prompt 层接口 — 每个 PromptLayer 代表最终 System Prompt 中的一个独立层。
 *
 * 设计原则：
 * 1. 每个 Layer 拥有自己的 render() 内容、priority() 排序权重、name() 标识
 * 2. ContextAssembler 不关心 Layer 的语义（Persona/Workspace/Memory），只负责按 priority 排序并渲染
 * 3. PromptPolicy 决定哪些 Layer 参与组装，Assembler 只负责渲染
 * 4. 新增 Layer 只需实现此接口，无需修改 Assembler 或 Policy
 *
 * 这与 OpenClaw 的 PromptLayer 设计一致：
 * - Assembler 是无业务知识的纯渲染器
 * - Policy 是业务决策者
 * - Layer 是数据载体
 */
public interface PromptLayer {

    /**
     * 渲染此层的内容。
     * 返回空字符串表示此层不参与最终 Prompt（由 Assembler 自动过滤）。
     */
    String render();

    /**
     * 排序优先级（数值越小越靠前）。
     * 建议范围：
     * 0-9   : 安全/锁定层（ModeLock, Safety）
     * 10-19 : 世界/环境层（WorldState, Environment）
     * 20-29 : 身份/人格层（Persona, Identity, Character）
     * 30-39 : 关系/群组层（GroupContext, Relationship）
     * 40-49 : 用户层（UserProfile）
     * 50-59 : 工作空间层（Workspace, HostContext）
     * 60-69 : 记忆层（Memory）
     * 70-79 : 知识/技能层（Knowledge, Skill）
     * 80-89 : 计划层（Plan）
     * 90-99 : 提示/规则层（ModeHint, ToolRules）
     */
    int priority();

    /**
     * 层名称（用于日志、调试、Policy 匹配）。
     */
    String name();
}