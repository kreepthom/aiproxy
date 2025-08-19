package com.aiproxy.core.config;

import com.aiproxy.common.constants.ApiConstants;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class WebClientConfig {
    
    @Value("${relay.proxy.enabled:false}")
    private boolean proxyEnabled;
    
    @Value("${relay.proxy.type:socks5}")
    private String proxyType;
    
    @Value("${relay.proxy.host:}")
    private String proxyHost;
    
    @Value("${relay.proxy.port:1080}")
    private int proxyPort;
    
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
    
    @Bean
    public WebClient claudeWebClient() {
        ConnectionProvider provider = ConnectionProvider.builder("claude-pool")
            .maxConnections(100)
            .maxIdleTime(Duration.ofSeconds(20))
            .maxLifeTime(Duration.ofMinutes(5))
            .pendingAcquireTimeout(Duration.ofSeconds(60))
            .evictInBackground(Duration.ofSeconds(120))
            .build();
        
        HttpClient httpClient = HttpClient.create(provider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .responseTimeout(Duration.ofMinutes(5))
            .doOnConnected(conn ->
                conn.addHandlerLast(new ReadTimeoutHandler(300, TimeUnit.SECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(60, TimeUnit.SECONDS))
            );
        
        return WebClient.builder()
            .baseUrl(ApiConstants.CLAUDE_BASE_URL)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader(ApiConstants.CLAUDE_VERSION_HEADER, ApiConstants.CLAUDE_VERSION)
            .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize(10 * 1024 * 1024)) // 10MB
            .filter(logRequest())
            .filter(logResponse())
            .build();
    }
    
    @Bean
    public WebClient geminiWebClient() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .responseTimeout(Duration.ofMinutes(5));
        
        return WebClient.builder()
            .baseUrl(ApiConstants.GEMINI_BASE_URL)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize(10 * 1024 * 1024))
            .filter(logRequest())
            .filter(logResponse())
            .build();
    }
    
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.debug("Request: {} {}", request.method(), request.url());
            return Mono.just(request);
        });
    }
    
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            log.debug("Response Status: {}", response.statusCode());
            return Mono.just(response);
        });
    }
}