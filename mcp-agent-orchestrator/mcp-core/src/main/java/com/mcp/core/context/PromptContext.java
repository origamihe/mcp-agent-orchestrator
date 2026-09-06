package com.mcp.core.context;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一上下文模型 — 各模块输出数据统一汇聚到此，再由 ContextAssembler 渲染。
 *
 * 设计原则：
 * 1. 这是纯数据模型，不做任何决策或格式化
 * 2. 每个字段为 null 时，表示该层不参与渲染
 * 3. 各模块（PromptComposer、MemoryRetriever、Planner 等）负责填充自己的字段
 * 4. ContextAssembler 负责将 PromptContext 渲染为最终 Prompt 字符串
 *
 * 数据层优先级（由 ContextAssembler 保证）：
 * MODE > WORLD > PERSONA > GROUP > USER > MEMORY > ARTIFACT > PLAN > MODE_HINT
 */
@Data
@Builder
public class PromptContext {

    /** 模式锁定（角色/游戏模式时的角色锁 Prompt） */
    private String modeLock;

    /** 世界状态（角色/游戏模式时的世界状态描述） */
    private String worldState;

    /** 基础系统提示词（Adapter 提供的原始系统指令，如 Agent 行为准则） */
    private String baseSystemPrompt;

    /** 核心身份 + 行为准则（PersonaMemoryStore 提供） */
    private String persona;

    /** 核心身份（Persona 中提取的身份声明部分） */
    private String identity;

    /** 角色特征（Persona 中提取的说话风格、语气、价值观） */
    private String character;

    /** 关系上下文（与用户的关系、历史交互模式） */
    private String relationship;

    /** 群上下文（群聊时的群设定） */
    private String groupContext;

    /** 群聊对话上下文（Current Thread + Recent Group Context + Relevant Group Memory） */
    private String groupConversation;

    /** 用户身份（角色、关系、权限） */
    private String userProfile;

    /** 记忆层（Working Memory + Long-term Memory） */
    private String memory;

    /** 计划层（Planner 生成的执行计划） */
    private String plan;

    /** 模式提示（语音/文字模式等特殊规则） */
    private String modeHint;

    /** 工作空间（跨会话的持久化工作状态） */
    private String workspace;

    /** Host 上下文（当前 Host 感知的环境状态） */
    private String hostContext;

    /** Artifact 上下文（活动文档/模组/代码的摘要 + 相关段落） */
    private String artifact;

    /** 日期上下文（当前日期、时间、时区，由 DateContextProvider 注入） */
    private String dateContext;

    /**
     * 将 PromptContext 转换为 PromptLayer 列表。
     * 每个非空字段生成一个独立的 PromptLayer，按 priority 排序后由 ContextAssembler 渲染。
     *
     * 优先级设计：
     * 0-9   : 安全/锁定层
     * 10-19 : 世界/环境层
     * 20-29 : 身份/人格层
     * 30-39 : 关系/群组层
     * 40-49 : 用户层
     * 50-59 : 工作空间层
     * 60-69 : 记忆层
     * 70-79 : 知识/技能层
     * 80-89 : 计划层
     * 90-99 : 提示/规则层
     */
    public List<PromptLayer> toLayers() {
        List<PromptLayer> layers = new ArrayList<>();
        addLayer(layers, "BASE_SYSTEM", 5, baseSystemPrompt);
        addLayer(layers, "MODE_LOCK", 0, modeLock);
        addLayer(layers, "WORLD_STATE", 10, worldState);
        addLayer(layers, "DATE_CONTEXT", 12, dateContext);
        addLayer(layers, "IDENTITY", 20, identity);
        addLayer(layers, "CHARACTER", 22, character);
        addLayer(layers, "RELATIONSHIP", 24, relationship);
        addLayer(layers, "PERSONA", 28, persona);
        addLayer(layers, "GROUP_CONTEXT", 30, groupContext);
        addLayer(layers, "GROUP_CONVERSATION", 35, groupConversation);
        addLayer(layers, "USER_PROFILE", 40, userProfile);
        addLayer(layers, "WORKSPACE", 50, workspace);
        addLayer(layers, "HOST_CONTEXT", 55, hostContext);
        addLayer(layers, "MEMORY", 60, memory);
        addLayer(layers, "ARTIFACT", 65, artifact);
        addLayer(layers, "PLAN", 80, plan);
        addLayer(layers, "MODE_HINT", 90, modeHint);
        return layers;
    }

    private static void addLayer(List<PromptLayer> layers, String name, int priority, String content) {
        if (content != null && !content.isEmpty()) {
            layers.add(new SimplePromptLayer(name, priority, content));
        }
    }

    /**
     * 创建空上下文（用于简单场景）。
     */
    public static PromptContext empty() {
        return PromptContext.builder().build();
    }
}