package com.mcp.tools.security;

/**
 * 权限拒绝异常
 */
public class PermissionDeniedException extends RuntimeException {
    public PermissionDeniedException(String message) {
        super(message);
    }

    public PermissionDeniedException(String userId, String requiredLevel) {
        super("权限不足：用户 " + userId + " 需要 " + requiredLevel + " 权限");
    }
}