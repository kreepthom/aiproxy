package com.aiproxy.auth.handler;

import com.aiproxy.common.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

@Component
@Slf4j
public class AuthenticationHandler implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {
    
    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        log.error("!!! AuthenticationHandler.commence() setting 401 - Path: {} !!!", 
            exchange.getRequest().getPath().value());
        log.warn("Authentication failed: {}", ex.getMessage());
        return handleError(exchange.getResponse(), HttpStatus.UNAUTHORIZED, 
            401, "未认证", "请先登录或提供有效的认证信息");
    }
    
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        log.warn("Access denied: {}", denied.getMessage());
        return handleError(exchange.getResponse(), HttpStatus.FORBIDDEN, 
            403, "无权限", "您没有权限访问此资源");
    }
    
    private Mono<Void> handleError(ServerHttpResponse response, HttpStatus status, 
                                   int code, String error, String message) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        String errorJson = JsonUtil.toJson(Map.of(
            "code", code,
            "error", error,
            "message", message,
            "timestamp", Instant.now().toString()
        ));
        
        DataBuffer buffer = response.bufferFactory()
            .wrap(errorJson.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}