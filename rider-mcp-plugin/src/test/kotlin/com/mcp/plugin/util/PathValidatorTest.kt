package com.mcp.plugin.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathValidatorTest {

    @Test
    fun `should allow path within workspace`() {
        assertTrue(PathValidator.isPathInWorkspace("/workspace/src/main.kt", "/workspace"))
    }

    @Test
    fun `should allow nested path within workspace`() {
        assertTrue(
            PathValidator.isPathInWorkspace(
                "/workspace/src/main/kotlin/com/package/App.kt",
                "/workspace"
            )
        )
    }

    @Test
    fun `should reject path outside workspace`() {
        assertFalse(PathValidator.isPathInWorkspace("/etc/passwd", "/workspace"))
    }

    @Test
    fun `should reject path traversal via dotdot`() {
        assertFalse(
            PathValidator.isPathInWorkspace("/workspace/../../../etc/passwd", "/workspace")
        )
    }

    @Test
    fun `should reject path traversal via nested dotdot`() {
        assertFalse(
            PathValidator.isPathInWorkspace("/workspace/subdir/../../etc/shadow", "/workspace")
        )
    }

    @Test
    fun `should reject path traversal with mixed separators`() {
        assertFalse(
            PathValidator.isPathInWorkspace(
                "/workspace\\..\\..\\Windows\\System32\\config\\SAM",
                "/workspace"
            )
        )
    }

    @Test
    fun `should reject null workspace`() {
        assertFalse(PathValidator.isPathInWorkspace("/workspace/src/main.kt", null))
    }

    @Test
    fun `should reject blank workspace`() {
        assertFalse(PathValidator.isPathInWorkspace("/workspace/src/main.kt", ""))
        assertFalse(PathValidator.isPathInWorkspace("/workspace/src/main.kt", "   "))
    }

    @Test
    fun `should detect sensitive path with ssh directory`() {
        assertTrue(PathValidator.isSensitivePath("/workspace/.ssh/id_rsa"))
    }

    @Test
    fun `should detect sensitive path with aws directory`() {
        assertTrue(PathValidator.isSensitivePath("/workspace/.aws/credentials"))
    }

    @Test
    fun `should detect sensitive path with config directory`() {
        assertTrue(PathValidator.isSensitivePath("/workspace/.config/secret"))
    }

    @Test
    fun `should detect sensitive file id_rsa`() {
        assertTrue(PathValidator.isSensitivePath("/workspace/id_rsa"))
    }

    @Test
    fun `should detect sensitive file id_ed25519`() {
        assertTrue(PathValidator.isSensitivePath("/workspace/id_ed25519"))
    }

    @Test
    fun `should detect sensitive file authorized_keys`() {
        assertTrue(PathValidator.isSensitivePath("/workspace/authorized_keys"))
    }

    @Test
    fun `should detect sensitive file dotenv`() {
        assertTrue(PathValidator.isSensitivePath("/workspace/.env"))
    }

    @Test
    fun `should detect env file in subdirectory`() {
        assertTrue(PathValidator.isSensitivePath("/workspace/subdir/.env"))
    }

    @Test
    fun `should detect credentials file`() {
        assertTrue(PathValidator.isSensitivePath("/workspace/credentials"))
    }

    @Test
    fun `should detect zshrc file`() {
        assertTrue(PathValidator.isSensitivePath("/workspace/.zshrc"))
    }

    @Test
    fun `should detect bashrc file`() {
        assertTrue(PathValidator.isSensitivePath("/workspace/.bashrc"))
    }

    @Test
    fun `should detect known_hosts file`() {
        assertTrue(PathValidator.isSensitivePath("/workspace/known_hosts"))
    }

    @Test
    fun `should detect profile file`() {
        assertTrue(PathValidator.isSensitivePath("/workspace/.profile"))
    }

    @Test
    fun `should detect Windows AppData path`() {
        assertTrue(
            PathValidator.isSensitivePath("C:\\Users\\test\\AppData\\Roaming\\secret")
        )
    }

    @Test
    fun `should detect Windows System32 path`() {
        assertTrue(
            PathValidator.isSensitivePath("C:\\Windows\\System32\\config\\SAM")
        )
    }

    @Test
    fun `should reject normal source file paths`() {
        assertFalse(PathValidator.isSensitivePath("/workspace/src/main.kt"))
        assertFalse(PathValidator.isSensitivePath("/workspace/README.md"))
        assertFalse(PathValidator.isSensitivePath("/workspace/build.gradle.kts"))
    }

    @Test
    fun `should normalize path with redundant separators`() {
        val result = PathValidator.normalizePath("/workspace//src///main.kt")
        assertTrue(result.contains("workspace"))
        assertTrue(result.contains("src"))
        assertTrue(result.contains("main.kt"))
    }

    @Test
    fun `should normalize path with dot segments`() {
        val result = PathValidator.normalizePath("/workspace/./src/./main.kt")
        assertTrue(result.contains("workspace"))
        assertTrue(result.contains("src"))
        assertTrue(result.contains("main.kt"))
    }

    @Test
    fun `should normalize path traversal patterns`() {
        val result = PathValidator.normalizePath("/workspace/a/../b/main.kt")
        assertTrue(result.contains("workspace"))
        assertTrue(result.contains("b"))
        assertTrue(result.contains("main.kt"))
    }

    @Test
    fun `should handle duplicate sensitive path checks`() {
        assertTrue(PathValidator.isSensitivePath("/workspace/.ssh/config"))
        assertTrue(PathValidator.isSensitivePath("/workspace/.ssh/known_hosts"))
    }

    @Test
    fun `should detect docker directory`() {
        assertTrue(PathValidator.isSensitivePath("/workspace/.docker/config.json"))
    }

    @Test
    fun `should detect gnupg directory`() {
        assertTrue(PathValidator.isSensitivePath("/workspace/.gnupg/private.key"))
    }

    @Test
    fun `should detect Linux etc path`() {
        assertTrue(PathValidator.isSensitivePath("/etc/shadow"))
    }
}