package com.mcp.llm.config;

import com.mcp.core.service.PromptService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * LLM 配置类 - 优先使用数据库中的 Prompt
 */
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class LlmConfig {

    // 引入日志组件，比单纯的 System.out 更规范，带有时间戳和线程信息
    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    /**
     * 创建 ChatClient，使用数据库中的 Prompt
     */
    @Bean
    public ChatClient chatClient(GoogleGenAiChatModel chatModel,
                                 PromptService promptService) {

        String defaultSystemPrompt = null;
        String fallbackPrompt = "你是一个专业、友好、高效的 AI Agent 助手。";

        try {
            log.info("▶ [LLM配置] 正在尝试从数据库加载核心系统 Prompt...");

            // 从数据库异步获取核心系统 Prompt（设置 5 秒超时，防止数据库卡死导致应用启动挂起）
            defaultSystemPrompt = promptService.getCoreSystemPrompt()
                    .block(java.time.Duration.ofSeconds(5));

            if (defaultSystemPrompt == null || defaultSystemPrompt.trim().isEmpty()) {
                // 数据库查到了，但是内容为空
                System.err.println("⚠️ [LLM配置警告] 数据库中的 System Prompt 为空！将启用本地默认兜底 Prompt。");
                log.warn("数据库中的 System Prompt 为空！将启用本地默认兜底 Prompt。");
                defaultSystemPrompt = fallbackPrompt;
            } else {
                log.info("✨ [LLM配置成功] 成功从数据库加载 System Prompt，长度: {} 字", defaultSystemPrompt.length());
            }

        } catch (Exception e) {
            // 保护机制：捕获所有异常（如数据库连不上、超时、表不存在等）
            System.err.println("❌ [LLM配置错误] 无法从数据库加载核心系统 Prompt！");
            System.err.println("❌ 错误详情: " + e.getMessage());

            // 打印堆栈信息便于排错
            e.printStackTrace();

            // 同时记录到日志文件
            log.error("从数据库加载核心系统 Prompt 失败，使用兜底配置", e);

            // 发生异常时，确保有兜底 Prompt 可用
            defaultSystemPrompt = fallbackPrompt;
        }

        return ChatClient.builder(chatModel)
                .defaultSystem(defaultSystemPrompt)
                .build();
    }

    /**
     * 注入 Builder
     */
    @Bean
    public ChatClient.Builder chatClientBuilder(GoogleGenAiChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }
}