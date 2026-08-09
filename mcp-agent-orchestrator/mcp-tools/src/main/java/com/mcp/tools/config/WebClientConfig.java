package com.mcp.tools.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

@Slf4j
@Configuration
public class WebClientConfig {

    @Value("${app.proxy.enabled:false}")
    private boolean proxyEnabled;

    @Value("${app.proxy.host:127.0.0.1}")
    private String proxyHost;

    @Value("${app.proxy.port:7890}")
    private int proxyPort;

    @Bean
    public WebClient webSearchWebClient() {
        HttpClient httpClient = HttpClient.create();

        if (proxyEnabled && proxyHost != null && !proxyHost.isBlank() && proxyPort > 0) {
            httpClient = httpClient.proxy(proxy ->
                    proxy.type(ProxyProvider.Proxy.HTTP)
                            .host(proxyHost)
                            .port(proxyPort));
            log.info("[WebClient] Proxy enabled: {}:{}", proxyHost, proxyPort);
        } else {
            log.info("[WebClient] Proxy disabled, direct connection");
        }

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }
}