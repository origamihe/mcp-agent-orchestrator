package com.mcp.plugin.util

import java.io.File
import java.nio.file.Path

/**
 * 路径安全校验工具 — 统一 workspace 边界检查、敏感路径过滤和路径规范化。
 *
 * CapabilityAdapter 和 DiffApplier 共用此工具，确保校验逻辑一致。
 */
object PathValidator {

    private val SENSITIVE_DIRS = setOf(
        ".ssh", ".aws", ".config", ".gnupg", ".docker",
        "AppData", "Windows", "System32", "/etc", "/root", "/home"
    )

    private val SENSITIVE_FILES = setOf(
        "id_rsa", "id_ed25519", "authorized_keys", "known_hosts",
        "credentials", ".env", ".bashrc", ".zshrc", ".profile"
    )

    fun isPathInWorkspace(filePath: String, workspaceRoot: String?): Boolean {
        if (workspaceRoot.isNullOrBlank()) return false
        return try {
            val normalized = normalizePath(filePath)
            val normalizedWorkspace = normalizePath(workspaceRoot)
            normalized.startsWith(normalizedWorkspace)
        } catch (e: Exception) {
            false
        }
    }

    fun isSensitivePath(filePath: String): Boolean {
        val normalized = normalizePath(filePath).lowercase()
        val parts = normalized.split(File.separator, "/", "\\").filter { it.isNotBlank() }

        return SENSITIVE_DIRS.any { sensitive ->
            parts.any { part -> part.lowercase() == sensitive.lowercase() }
        } || SENSITIVE_FILES.any { sensitive ->
            parts.lastOrNull()?.lowercase() == sensitive.lowercase()
        }
    }

    fun normalizePath(filePath: String): String {
        return try {
            Path.of(filePath).normalize().toString()
        } catch (e: Exception) {
            filePath
        }
    }
}