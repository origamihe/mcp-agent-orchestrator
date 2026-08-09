package com.mcp.core.service.boundary;

import com.mcp.common.identity.UserRole;

/**
 * 记忆边界规则接口 - 规则引擎的核心抽象。
 *
 * 每条规则独立判断：一条 UserMemory 是否与 Persona 冲突。
 * 规则可以声明哪些角色可以覆盖（override）该规则。
 *
 * 设计原则：
 * - 单一职责：每条规则只负责一种冲突类型
 * - 可扩展：新增规则只需实现此接口并注册到 MemoryBoundaryGuard
 * - 权限感知：规则可声明哪些角色可绕过检查
 */
public interface MemoryBoundaryRule {

    /**
     * 规则名称，用于日志和调试。
     */
    String name();

    /**
     * 检查该规则是否适用于给定的用户记忆和 Persona。
     *
     * @param userMemory   用户记忆内容
     * @param personaText  Persona 原始文本
     * @return true 表示该规则适用于此场景
     */
    boolean matches(String userMemory, String personaText);

    /**
     * 判断是否存在冲突。
     * 仅在 matches() 返回 true 时才会被调用。
     *
     * @param userMemory   用户记忆内容
     * @param personaText  Persona 原始文本
     * @return true 表示存在冲突
     */
    boolean isConflict(String userMemory, String personaText);

    /**
     * 返回可以覆盖此规则的最低角色等级。
     * 例如：返回 UserRole.OWNER 表示只有 OWNER 可以绕过此规则；
     * 返回 UserRole.ADMIN 表示 ADMIN 及以上可以绕过；
     * 返回 null 表示任何角色都不能绕过（安全规则）。
     *
     * @return 可覆盖此规则的最低角色，null 表示不可覆盖
     */
    UserRole overrideMinimumRole();
}