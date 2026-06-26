package com.mcp.gateway.tts;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
public class VoiceFileManager {

    @Value("${tts.output-dir:./voice-output}")
    private String outputDir;

    @PostConstruct
    public void init() {
        try {
            Path dir = Paths.get(outputDir);
            Files.createDirectories(dir);
            log.info("[VoiceFileManager] Voice output dir: {}", dir.toAbsolutePath());
        } catch (Exception e) {
            log.error("[VoiceFileManager] Failed to create output dir", e);
        }
    }

    /**
     * 获取语音文件的 HTTP 可访问 URL
     */
    public String getVoiceUrl(String fileName) {
        return "/voice/" + fileName;
    }

    /**
     * 配置静态资源映射，让语音文件可通过 HTTP 访问
     */
    @Configuration
    public static class VoiceResourceConfig implements WebFluxConfigurer {
        @Value("${tts.output-dir:./voice-output}")
        private String outputDir;

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
            Path absPath = Paths.get(outputDir).toAbsolutePath();
            registry.addResourceHandler("/voice/**")
                    .addResourceLocations("file:" + absPath.toString().replace("\\", "/") + "/");
        }
    }
}