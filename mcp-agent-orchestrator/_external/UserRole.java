package com.mcp.common.identity;

/**
 * 用户角色 - 决定权限等级
 * OWNER > ADMIN > MEMBER
 */
public enum UserRole {
    OWNER,
    ADMIN,
    MEMBER;

    public boolean isAtLeast(UserRole other) {
        return this.ordinal() <= other.ordinal();
    }

    public boolean canManagePersona() {
        return this == OWNER;
    }

    public boolean canManageMemory() {
        return this == OWNER || this == ADMIN;
    }

    public boolean canManageAgent() {
        return this == OWNER;
    }
}
