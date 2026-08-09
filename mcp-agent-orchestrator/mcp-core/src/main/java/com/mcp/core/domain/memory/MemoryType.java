package com.mcp.core.domain.memory;

/**
 * 记忆类型 - 决定生命周期和检索策略
 *
 * 生命周期层级：
 *   PERMANENT（永久）: PROFILE, RELATION
 *   LONG（长期）: PREFERENCE, HABIT, GOAL, PROJECT, FACT
 *   MEDIUM（中期）: EVENT
 *   SHORT（短期）: TEMPORARY
 */
public enum MemoryType {
    PROFILE("用户资料", "身份、职业、技能等基本属性", Lifecycle.PERMANENT),
    IDENTITY("身份信息", "用户身份、称呼偏好、角色等", Lifecycle.PERMANENT),
    PREFERENCE("喜好", "喜欢/不喜欢的事物", Lifecycle.LONG),
    HABIT("习惯", "经常重复的行为模式", Lifecycle.LONG),
    GOAL("长期目标", "用户正在追求的目标", Lifecycle.LONG),
    PROJECT("项目", "正在进行的项目/工作", Lifecycle.LONG),
    FACT("事实", "可验证的客观事实", Lifecycle.LONG),
    RELATION("人物关系", "与其他人的关系", Lifecycle.PERMANENT),
    SKILL("技能", "用户具备的技能或能力", Lifecycle.LONG),
    SCHEDULE("日程", "日程安排、时间计划", Lifecycle.MEDIUM),
    TEMPORARY("临时信息", "短期有效的信息", Lifecycle.SHORT),
    EVENT("重要事件", "值得记住的事件", Lifecycle.MEDIUM);

    private final String displayName;
    private final String description;
    private final Lifecycle lifecycle;

    MemoryType(String displayName, String description, Lifecycle lifecycle) {
        this.displayName = displayName;
        this.description = description;
        this.lifecycle = lifecycle;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public Lifecycle getLifecycle() { return lifecycle; }

    public enum Lifecycle {
        PERMANENT,  // 永不过期，不衰减
        LONG,       // 长期保留，缓慢衰减
        MEDIUM,     // 中期保留，正常衰减
        SHORT       // 短期保留，快速衰减
    }
}