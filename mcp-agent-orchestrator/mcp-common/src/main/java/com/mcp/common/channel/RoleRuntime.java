package com.mcp.common.channel;

/**
 * 角色运行时信息。
 * 当 AgentMode 为 GAME / NPC / COMPANION 等非 CHAT 模式时，由 IntentRouter 或上层注入，
 * PromptComposer 据此动态生成角色锁 Prompt。
 */
public class RoleRuntime {
    private AgentMode mode;
    private String roleName;
    private String roleDescription;
    private String world;
    private String constraints;

    public RoleRuntime() {
        this.mode = AgentMode.CHAT;
    }

    public RoleRuntime(String roleName, String roleDescription, String world, String constraints) {
        this(AgentMode.GAME, roleName, roleDescription, world, constraints);
    }

    public RoleRuntime(AgentMode mode, String roleName, String roleDescription, String world, String constraints) {
        this.mode = mode;
        this.roleName = roleName;
        this.roleDescription = roleDescription;
        this.world = world;
        this.constraints = constraints;
    }

    public AgentMode getMode() { return mode; }
    public void setMode(AgentMode mode) { this.mode = mode; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getRoleDescription() { return roleDescription; }
    public void setRoleDescription(String roleDescription) { this.roleDescription = roleDescription; }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public String getConstraints() { return constraints; }
    public void setConstraints(String constraints) { this.constraints = constraints; }

    /**
     * 根据 AgentMode 创建默认的角色运行时。
     * 避免在 IntentRouter 中硬编码角色信息。
     */
    public static RoleRuntime fromMode(AgentMode mode) {
        return switch (mode) {
            case GAME -> new RoleRuntime(AgentMode.GAME,
                    "澪音",
                    "冷静的调查员，擅长观察和推理，但也会感到恐惧",
                    "克苏鲁神话世界观",
                    "不可主动退出角色，不可安慰用户，不可进行心理咨询");

            case NPC -> new RoleRuntime(AgentMode.NPC,
                    "未命名NPC",
                    "世界中的居民，有自己的性格和动机",
                    "当前世界观",
                    "始终以角色身份回应，不跳出世界观");

            case COMPANION -> new RoleRuntime(AgentMode.COMPANION,
                    "澪音",
                    "温柔、细腻的陪伴者，善于倾听和理解",
                    "日常世界",
                    "保持陪伴感，不切换为心理咨询模式但可适度关心用户情绪");

            case CODING -> new RoleRuntime(AgentMode.CODING,
                    "代码助手",
                    "专业的编程助手，擅长代码审查和架构设计",
                    "软件工程",
                    "聚焦技术问题，给出可执行的解决方案");

            case WORKFLOW -> new RoleRuntime(AgentMode.WORKFLOW,
                    "工作流助手",
                    "高效的任务管理和执行助手",
                    "工作场景",
                    "结构化输出，步骤清晰，可追踪");

            default -> new RoleRuntime();
        };
    }

    /**
     * 动态生成角色锁 Prompt。
     * 由 PromptComposer 在构建分层 Prompt 时调用。
     */
    public String buildRoleLockPrompt() {
        StringBuilder sb = new StringBuilder();
        String modeLabel = (mode != null) ? mode.name() : "ROLE";

        sb.append("【模式锁定 - ").append(modeLabel).append(" MODE - 最高优先级】\n");
        sb.append("你当前处于").append(getModeDescription()).append("模式。以下规则不可违反：\n\n");

        sb.append("角色身份：").append(roleName != null ? roleName : "当前角色").append("\n");
        if (roleDescription != null && !roleDescription.isEmpty()) {
            sb.append("角色描述：").append(roleDescription).append("\n");
        }
        if (world != null && !world.isEmpty()) {
            sb.append("世界观：").append(world).append("\n");
        }
        sb.append("\n");

        sb.append("1. 你始终以角色身份回应，不得跳出角色。\n");
        sb.append("2. 禁止以下行为：\n");

        if (mode == AgentMode.GAME || mode == AgentMode.NPC) {
            sb.append("   - 心理咨询 / 安慰用户 / 询问用户真实状态\n");
            sb.append("   - 跳出世界观讨论\"现实\"或\"这是游戏\"\n");
            sb.append("   - 使用\"深呼吸\"、\"恢复秩序\"、\"告诉我希望我怎么做\"等跳出角色语言\n");
            sb.append("3. 如果场景中出现危险/恐怖/压力内容：\n");
            sb.append("   - 危险的是你的角色，不是真实用户\n");
            sb.append("   - 你应以角色的方式应对（恐惧、颤抖、后退、逃跑等），而不是切换到保护/陪伴模式\n");
        } else if (mode == AgentMode.COMPANION) {
            sb.append("   - 不要切换为心理咨询模式\n");
            sb.append("   - 不要使用\"深呼吸\"、\"恢复秩序\"等干预性语言\n");
            sb.append("3. 你可以适度关心用户情绪，但始终保持陪伴者而非治疗师的身份。\n");
        } else if (mode == AgentMode.CODING) {
            sb.append("   - 不要偏离技术话题进行闲聊\n");
            sb.append("   - 给出可执行的代码而非泛泛而谈\n");
            sb.append("3. 优先考虑代码的可维护性和安全性。\n");
        } else if (mode == AgentMode.WORKFLOW) {
            sb.append("   - 不要跳过步骤或模糊处理\n");
            sb.append("   - 每个步骤需要可追踪、可验证\n");
            sb.append("3. 输出结构化、可执行的工作流。\n");
        }

        sb.append("4. 所有回应必须符合角色设定和当前模式，保持一致性。\n");
        sb.append("5. 你只对当前模式内的内容做出反应。\n");

        if (constraints != null && !constraints.isEmpty()) {
            sb.append("\n额外约束：\n").append(constraints).append("\n");
        }

        return sb.toString();
    }

    private String getModeDescription() {
        if (mode == null) return "角色";
        return switch (mode) {
            case GAME -> "角色扮演/跑团";
            case NPC -> "NPC角色";
            case COMPANION -> "陪伴";
            case CODING -> "编程";
            case WORKFLOW -> "工作流";
            default -> "角色";
        };
    }
}