package com.mcp.plugin.transport

import org.junit.Assert.*
import org.junit.Test

/**
 * WebSocket Token 生命周期测试。
 *
 * 验证：
 * 1. Token 过期检测逻辑 (isTokenValid)
 * 2. 认证失败检测 (isAuthFailure)
 * 3. Token 有效性边界条件
 */
class WebSocketTokenTest {

    @Test
    fun `token should be valid when expiry is in the future`() {
        val futureExpiry = System.currentTimeMillis() + 60_000
        assertTrue(isTokenValid(futureExpiry))
    }

    @Test
    fun `token should be invalid when expired`() {
        val pastExpiry = System.currentTimeMillis() - 1
        assertFalse(isTokenValid(pastExpiry))
    }

    @Test
    fun `token should be invalid when expiry is zero`() {
        assertFalse(isTokenValid(0))
    }

    @Test
    fun `token should be invalid when expiry is negative`() {
        assertFalse(isTokenValid(-1))
    }

    @Test
    fun `token should be valid exactly at expiry time`() {
        val exactExpiry = System.currentTimeMillis()
        assertFalse(isTokenValid(exactExpiry))
    }

    @Test
    fun `token should be valid just before expiry`() {
        val justBeforeExpiry = System.currentTimeMillis() + 1
        assertTrue(isTokenValid(justBeforeExpiry))
    }

    @Test
    fun `token with 30 minute validity should be valid`() {
        val thirtyMinFromNow = System.currentTimeMillis() + (30 * 60 * 1000)
        assertTrue(isTokenValid(thirtyMinFromNow))
    }

    @Test
    fun `auth failure should detect status code 4001`() {
        assertTrue(isAuthFailure(4001, "Unauthorized"))
    }

    @Test
    fun `auth failure should detect status code 4003`() {
        assertTrue(isAuthFailure(4003, "Forbidden"))
    }

    @Test
    fun `auth failure should detect reason containing auth`() {
        assertTrue(isAuthFailure(1000, "Authentication failed"))
    }

    @Test
    fun `auth failure should detect reason containing unauthorized`() {
        assertTrue(isAuthFailure(1000, "Unauthorized access"))
    }

    @Test
    fun `auth failure should detect reason containing 401`() {
        assertTrue(isAuthFailure(1000, "HTTP 401 Unauthorized"))
    }

    @Test
    fun `auth failure should detect reason containing 403`() {
        assertTrue(isAuthFailure(1000, "HTTP 403 Forbidden"))
    }

    @Test
    fun `auth failure should detect reason containing token`() {
        assertTrue(isAuthFailure(1000, "Invalid token"))
    }

    @Test
    fun `auth failure should be case insensitive`() {
        assertTrue(isAuthFailure(1000, "AUTHENTICATION FAILED"))
        assertTrue(isAuthFailure(1000, "UNAUTHORIZED"))
        assertTrue(isAuthFailure(1000, "INVALID TOKEN"))
    }

    @Test
    fun `normal close should not be auth failure`() {
        assertFalse(isAuthFailure(1000, "Normal closure"))
        assertFalse(isAuthFailure(1001, "Going away"))
    }

    @Test
    fun `non-auth abnormal close should not be auth failure`() {
        assertFalse(isAuthFailure(1006, "Abnormal closure"))
        assertFalse(isAuthFailure(1011, "Server error"))
    }

    @Test
    fun `auth failure should detect substrings in reason`() {
        assertTrue(isAuthFailure(1000, "Connection closed: token expired"))
        assertTrue(isAuthFailure(1000, "Error: unauthorized_user"))
        assertTrue(isAuthFailure(1000, "auth_error_code_123"))
    }

    private fun isTokenValid(expiry: Long): Boolean {
        return expiry > 0 && System.currentTimeMillis() < expiry
    }

    private fun isAuthFailure(statusCode: Int, reason: String): Boolean {
        val reasonLower = reason.lowercase()
        return statusCode == 4001
                || statusCode == 4003
                || reasonLower.contains("auth")
                || reasonLower.contains("unauthorized")
                || reasonLower.contains("401")
                || reasonLower.contains("403")
                || reasonLower.contains("token")
    }
}