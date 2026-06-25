package com.mcp.core.domain.memory;

/**
 * 记忆类型 - 区分记忆的性质
 * 替代原来的 MemoryCategory 中过于宽泛的分类
 */
public enum MemoryType {
    FACT,
    PREFERENCE,
    RELATION,
    EXPERIENCE,
    SYSTEM
}