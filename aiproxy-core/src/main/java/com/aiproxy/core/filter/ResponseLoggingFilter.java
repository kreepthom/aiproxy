package com.aiproxy.core.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ResponseLoggingFilter implements WebFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        String path = request.getPath().value();
        
        return chain.filter(exchange)
            .doOnSuccess(aVoid -> {
                log.info(">>> Response Status for [{}]: {} <<<", 
                    path, response.getStatusCode());
                
                if (path.contains("/admin/accounts/authorize-url")) {
                    log.warn("!!! Special logging for authorize-url - Status: {} !!!", 
                        response.getStatusCode());
                }
            })
            .doOnError(error -> {
                log.error(">>> Error for [{}]: {} <<<", path, error.getMessage());
            });
    }
}