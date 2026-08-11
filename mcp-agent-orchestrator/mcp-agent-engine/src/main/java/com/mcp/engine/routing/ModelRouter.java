package com.mcp.engine.routing;

import com.mcp.core.domain.llm.LlmModelConfig;
import com.mcp.core.domain.llm.ProviderAvailability;
import com.mcp.core.service.LlmConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

/**
 * 模型路由器 — 根据任务复杂度自动选择最优模型。
 *
 * 轻量任务（问候/闲聊）→ 小模型（更快）
 * 搜索/代码生成 → 大模型（更强）
 * 默认 → 标准模型
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelRouter {

    private final LlmConfigService llmConfigService;
    private final ProviderAvailability providerAvailability;

    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "^(你好|hi|hello|hey|早上好|下午好|晚上好|晚安|再见|bye|goodbye|谢谢|thank|thanks|不客气|ok|好的|嗯|哦|啊|哈)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SHORT_CHAT_PATTERN = Pattern.compile(
            "^.{1,15}$");

    private static final Pattern CODE_PATTERN = Pattern.compile(
            "(代码|code|编程|program|写|write|生成|generate|实现|implement|修复|fix|bug|debug|"
                    + "函数|function|类|class|方法|method|算法|algorithm|测试|test|单元测试|"
                    + "编译|compile|部署|deploy|docker|kubernetes|k8s|SQL|sql|数据库|database|"
                    + "API|api|接口|interface|框架|framework|spring|react|vue|angular)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SEARCH_PATTERN = Pattern.compile(
            "(搜索|search|查找|查找|检索|查询|query|是什么|什么是|什么是|怎么|如何|how|what|why|"
                    + "为什么|介绍|说明|解释|explain|描述|describe|概括|总结|总结|分析|analyze|"
                    + "比较|对比|compare|区别|差异|difference|最新|最近|新闻|news|趋势|trend)",
            Pattern.CASE_INSENSITIVE);

    public enum TaskComplexity {
        GREETING,
        SHORT_CHAT,
        CHAT,
        SEARCH,
        CODE_GENERATION,
        DEFAULT
    }

    public Mono<TaskComplexity> classifyTask(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return Mono.just(TaskComplexity.DEFAULT);
        }

        String trimmed = userMessage.trim();

        if (GREETING_PATTERN.matcher(trimmed).matches()) {
            return Mono.just(TaskComplexity.GREETING);
        }

        if (CODE_PATTERN.matcher(trimmed).find()) {
            return Mono.just(TaskComplexity.CODE_GENERATION);
        }

        if (SEARCH_PATTERN.matcher(trimmed).find()) {
            return Mono.just(TaskComplexity.SEARCH);
        }

        if (SHORT_CHAT_PATTERN.matcher(trimmed).matches()) {
            return Mono.just(TaskComplexity.SHORT_CHAT);
        }

        return Mono.just(TaskComplexity.DEFAULT);
    }

    public Mono<LlmModelConfig> selectModel(String userMessage) {
        return classifyTask(userMessage)
                .flatMap(this::selectModelForComplexity);
    }

    public Mono<LlmModelConfig> selectModelForComplexity(TaskComplexity complexity) {
        switch (complexity) {
            case GREETING, SHORT_CHAT -> {
                return llmConfigService.getDefaultConfig()
                        .flatMap(this::findFastModel);
            }
            case CHAT -> {
                return llmConfigService.getDefaultConfig()
                        .flatMap(this::findFastModel);
            }
            case SEARCH, CODE_GENERATION -> {
                return llmConfigService.getDefaultConfig();
            }
            default -> {
                return llmConfigService.getDefaultConfig();
            }
        }
    }

    private Mono<LlmModelConfig> findFastModel(LlmModelConfig defaultConfig) {
        return llmConfigService.getAllEnabledConfigs()
                .flatMapMany(Flux::fromIterable)
                .filter(config -> config.isEnabled()
                        && providerAvailability.isProviderAvailable(config.getProvider()))
                .filter(config -> {
                    String modelName = config.getModelName().toLowerCase();
                    return modelName.contains("0.5b") || modelName.contains("1.5b")
                            || modelName.contains("tiny") || modelName.contains("mini")
                            || modelName.contains("small") || modelName.contains("nano")
                            || modelName.contains("lite");
                })
                .next()
                .switchIfEmpty(Mono.just(defaultConfig))
                .doOnNext(config -> log.info("[ModelRouter] Fast model selected: {}/{} for light task",
                        config.getProvider().getCode(), config.getModelName()));
    }
}