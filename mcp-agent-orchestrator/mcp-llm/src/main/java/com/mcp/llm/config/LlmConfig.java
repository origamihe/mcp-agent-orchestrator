package com.mcp.llm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * LLM 配置类 - 支持多 Provider（Ollama / Google AI Studio / OpenRouter / DeepSeek / Claude）
 * Provider 选择由数据库配置决定，而非 Spring Bean 条件判断
 */
@Configuration
public class LlmConfig {

    /**
     * 自定义 RestClient.Builder，延长超时时间以适应 CPU 推理速度（240秒）
     */
    @Bean
    @Primary
    public RestClient.Builder restClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(240));
        return RestClient.builder()
                .requestFactory(new ReactorClientHttpRequestFactory(httpClient));
    }
}