package com.mcp.gateway.config;

import com.mcp.tools.sandbox.ProcessSandboxExecutor;
import com.mcp.tools.sandbox.SandboxExecutor;
import com.mcp.tools.sandbox.SandboxPolicy;
import com.mcp.tools.sandbox.WorkspaceSandbox;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import java.nio.file.Path;

@Configuration
public class GatewayConfig {

    @Value("${mcp.sandbox.workspace-root:./workspace}")
    private String workspaceRootPath;

    @Bean
    public WebFluxConfigurer corsConfigurer() {
        return new WebFluxConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOriginPatterns("http://localhost:*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }

    @Bean
    public WorkspaceSandbox workspaceSandbox() {
        Path workspaceRoot = Path.of(workspaceRootPath).toAbsolutePath().normalize();
        return new WorkspaceSandbox(workspaceRoot);
    }

    @Bean
    public SandboxExecutor processSandboxExecutor() {
        return new ProcessSandboxExecutor();
    }

    @Bean
    public SandboxPolicy sandboxPolicy(WorkspaceSandbox workspaceSandbox, SandboxExecutor processSandboxExecutor) {
        return new SandboxPolicy(workspaceSandbox, processSandboxExecutor);
    }
}