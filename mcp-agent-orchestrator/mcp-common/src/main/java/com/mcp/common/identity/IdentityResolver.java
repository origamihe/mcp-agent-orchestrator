package com.mcp.common.identity;

/**
 * 身份解析器接口 - 从 sessionId 构建 MemoryIdentity。
 *
 * 未来演进路径：
 *   1. DefaultIdentityResolver（当前：解析 sessionId 字符串）
 *   2. DbIdentityResolver（未来：查询 ChatSessionEntity）
 *   3. 接口实现可替换，调用方无感知
 */
public interface IdentityResolver {

    MemoryIdentity resolve(String sessionId);
}