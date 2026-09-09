package com.mcp.plugin.util

import com.intellij.openapi.diagnostic.Logger

/**
 * 统一的异常处理器，提供一致的错误日志记录和恢复策略。
 *
 * 设计原则：
 * - 所有 catch 块通过此工具记录，确保日志格式一致
 * - 区分 retryable 和 non-retryable 错误
 * - 提供默认降级值，避免 null 传播
 */
object ExceptionHandler {

    private val logger = Logger.getInstance(ExceptionHandler::class.java)

    /**
     * 记录并处理异常，返回降级值。
     *
     * @param context 错误发生的上下文描述
     * @param e 捕获的异常
     * @param fallback 降级返回值
     * @return 降级值
     */
    fun <T> handle(context: String, e: Throwable, fallback: T): T {
        logger.error("[$context] ${e.message}", e)
        PluginLogger.error(context, e.message ?: "Unknown error", e)
        return fallback
    }

    /**
     * 记录并处理异常，包含额外上下文信息。
     *
     * @param context 错误发生的上下文描述
     * @param details 额外上下文信息
     * @param e 捕获的异常
     * @param fallback 降级返回值
     * @return 降级值
     */
    fun <T> handle(context: String, details: Map<String, Any?>, e: Throwable, fallback: T): T {
        logger.error("[$context] ${e.message} | details: $details", e)
        PluginLogger.error(context, "${e.message} | details: $details", e)
        return fallback
    }

    /**
     * 判断异常是否可重试。
     * 网络相关异常通常可重试，其他异常不可重试。
     */
    fun isRetryable(e: Throwable): Boolean {
        val className = e.javaClass.name.lowercase()
        val message = (e.message ?: "").lowercase()
        return className.contains("timeout")
                || className.contains("connect")
                || className.contains("io")
                || className.contains("socket")
                || message.contains("timeout")
                || message.contains("connection")
                || message.contains("refused")
                || message.contains("unreachable")
                || message.contains("reset")
    }

    /**
     * 安全执行操作，捕获异常并返回降级值。
     * 适用于不抛异常的方法调用。
     *
     * @param context 操作上下文描述
     * @param fallback 失败时的降级返回值
     * @param block 要执行的操作
     * @return 操作结果或降级值
     */
    fun <T> safeExecute(context: String, fallback: T, block: () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            handle(context, e, fallback)
        }
    }

    /**
     * 安全执行操作，静默捕获异常。
     * 适用于非关键路径，失败不影响主流程。
     *
     * @param context 操作上下文描述
     * @param block 要执行的操作
     */
    fun safeExecuteQuiet(context: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            logger.debug("[$context] Non-critical operation failed: ${e.message}")
        }
    }
}