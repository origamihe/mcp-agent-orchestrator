package com.mcp.plugin.util

import com.intellij.openapi.vfs.VirtualFile

object LanguageDetector {

    private val extensionMap = mapOf(
        "java" to "java",
        "kt" to "kotlin",
        "kts" to "kotlin",
        "cs" to "csharp",
        "py" to "python",
        "js" to "javascript",
        "jsx" to "javascript",
        "ts" to "typescript",
        "tsx" to "typescript",
        "go" to "go",
        "rs" to "rust",
        "rb" to "ruby",
        "php" to "php",
        "swift" to "swift",
        "c" to "c",
        "h" to "c",
        "cpp" to "cpp",
        "hpp" to "cpp",
        "cc" to "cpp",
        "cxx" to "cpp"
    )

    fun detect(file: VirtualFile): String {
        return extensionMap[file.extension?.lowercase()] ?: file.fileType.name.lowercase()
    }
}