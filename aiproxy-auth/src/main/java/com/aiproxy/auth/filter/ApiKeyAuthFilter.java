package com.aiproxy.auth.filter;

import com.aiproxy.auth.model.AdminAuthentication;
import com.aiproxy.auth.service.AdminAuthService;
import com.aiproxy.auth.service.ApiKeyService;
import com.aiproxy.common.constants.ApiConstants;
import com.aiproxy.common.exception.AuthenticationException;
import com.aiproxy.common.model.ApiKey;
import com.aiproxy.common.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

@Component
@Slf4j
public class ApiKeyAuthFilter implements WebFilter {
    
    private final ApiKeyService apiKeyService;
    private final AdminAuthService adminAuthService;
    
    public ApiKeyAuthFilter(ApiKeyService apiKeyService, AdminAuthService adminAuthService) {
        this.apiKeyService = apiKeyService;
        this.adminAuthService = adminAuthService;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        
        // Skip authentication for public endpoints
        if (shouldSkipAuth(path)) {
            log.debug("Skipping authentication for path: {}", path);
            return chain.filter(exchange);
        }
        
        // Extract token/key from Authorization header
        String authHeader = request.getHeaders().getFirst(ApiConstants.AUTH_HEADER);
        
        // Check X-API-Key header for API endpoints
        String apiKeyHeader = request.getHeaders().getFirst(ApiConstants.API_KEY_HEADER);
        
        // For admin endpoints, require Bearer token (admin login token)
        if (path.startsWith("/admin/")) {
            log.info("Processing admin endpoint: {}, Auth header: {}", path, 
                authHeader != null ? authHeader.substring(0, Math.min(authHeader.length(), 20)) + "..." : "null");
            
            if (authHeader == null || !authHeader.startsWith(ApiConstants.BEARER_PREFIX)) {
                log.warn("Missing or invalid auth header for admin endpoint: {}", path);
                return handleAuthError(exchange.getResponse(), "请提供管理员登录凭证");
            }
            
            String token = authHeader.substring(ApiConstants.BEARER_PREFIX.length()).trim();
            log.info("Extracted token for validation: {}...", token.substring(0, Math.min(token.length(), 8)));
            
            // 检查是否已经验证过（避免重复验证）
            if (exchange.getAttributes().containsKey("isAdmin")) {
                log.debug("Already authenticated, skipping validation");
                return chain.filter(exchange);
            }
            
            // 验证是否是有效的管理员 token
            return adminAuthService.validateToken(token)
                .doOnNext(valid -> log.info("Token validation result: {}", valid))
                .flatMap(valid -> {
                    if (Boolean.TRUE.equals(valid)) {
                        log.info("Admin token validated successfully for path: {}", path);
                        exchange.getAttributes().put("isAdmin", true);
                        
                        // 设置认证信息，让 Spring Security 知道用户已认证
                        var authentication = new AdminAuthentication(token);
                        authentication.setAuthenticated(true);
                        var securityContext = new SecurityContextImpl(authentication);
                        
                        // 将认证信息存储在 exchange 的 attributes 中
                        exchange.getAttributes().put("securityContext", securityContext);
                        
                        return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
                    } else {
                        log.warn("Invalid admin token for path: {}, validation returned: {}", path, valid);
                        return handleAuthError(exchange.getResponse(), "管理员登录已过期或无效");
                    }
                })
                .onErrorResume(error -> {
                    log.error("Error validating token for path {}: ", path, error);
                    return handleAuthError(exchange.getResponse(), "认证服务异常");
                })
                .doOnTerminate(() -> {
                    log.debug("Admin auth completed for path: {}, response committed: {}", 
                        path, exchange.getResponse().isCommitted());
                });
        }
        
        // For API endpoints, accept both Bearer token (API key) or X-API-Key header
        if (path.startsWith("/api/")) {
            String apiKey = null;
            
            if (authHeader != null && authHeader.startsWith(ApiConstants.BEARER_PREFIX)) {
                apiKey = authHeader.substring(ApiConstants.BEARER_PREFIX.length()).trim();
            } else if (apiKeyHeader != null) {
                apiKey = apiKeyHeader.trim();
            }
            
            if (apiKey == null) {
                return handleAuthError(exchange.getResponse(), "请提供 API Key");
            }
            
            return authenticateWithApiKey(apiKey, exchange, chain);
        }
        
        // For auth endpoints (like /auth/check), validate admin token
        if (path.startsWith("/auth/")) {
            if (authHeader == null || !authHeader.startsWith(ApiConstants.BEARER_PREFIX)) {
                return handleAuthError(exchange.getResponse(), "需要管理员登录凭证");
            }
            
            String token = authHeader.substring(ApiConstants.BEARER_PREFIX.length()).trim();
            
            return adminAuthService.validateToken(token)
                .flatMap(valid -> {
                    if (Boolean.TRUE.equals(valid)) {
                        log.debug("Admin token validated successfully for auth endpoint: {}", path);
                        var authentication = new AdminAuthentication(token);
                        authentication.setAuthenticated(true);
                        var securityContext = new SecurityContextImpl(authentication);
                        
                        exchange.getAttributes().put("securityContext", securityContext);
                        
                        return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
                    } else {
                        log.warn("Invalid admin token for auth endpoint: {}", path);
                        return handleAuthError(exchange.getResponse(), "管理员登录已过期或无效");
                    }
                })
                .onErrorResume(error -> {
                    log.error("Error validating token for auth endpoint: ", error);
                    return handleAuthError(exchange.getResponse(), "认证服务异常");
                });
        }
        
        // 其他路径需要认证
        return handleAuthError(exchange.getResponse(), "需要认证才能访问");
    }
    
    private Mono<Void> authenticateWithApiKey(String apiKey, ServerWebExchange exchange, WebFilterChain chain) {
        return apiKeyService.validateApiKey(apiKey)
            .flatMap(keyInfo -> {
                if (!keyInfo.isValid()) {
                    return handleAuthError(exchange.getResponse(), "API Key 无效或已过期");
                }
                
                // Check client restrictions
                if (!checkClientRestriction(keyInfo, exchange.getRequest())) {
                    return handleAuthError(exchange.getResponse(), "客户端不允许");
                }
                
                // Store API key info in exchange attributes
                exchange.getAttributes().put("apiKey", keyInfo);
                
                // Continue with the filter chain
                return chain.filter(exchange);
            })
            .switchIfEmpty(Mono.defer(() -> {
                // Only handle error if response is not committed
                if (!exchange.getResponse().isCommitted()) {
                    return handleAuthError(exchange.getResponse(), "无效的 API Key");
                }
                log.debug("Response already committed, skipping auth error for invalid API key");
                return Mono.empty();
            }));
    }
    
    private String extractApiKey(ServerHttpRequest request) {
        // Try Authorization header first
        String auth = request.getHeaders().getFirst(ApiConstants.AUTH_HEADER);
        if (auth != null && auth.startsWith(ApiConstants.BEARER_PREFIX)) {
            return auth.substring(ApiConstants.BEARER_PREFIX.length());
        }
        
        // Try X-API-Key header
        return request.getHeaders().getFirst(ApiConstants.API_KEY_HEADER);
    }
    
    private boolean shouldSkipAuth(String path) {
        return path.startsWith("/health") || 
               path.startsWith("/actuator") || 
               path.equals("/auth/login") ||  // 登录接口跳过认证
               path.startsWith("/oauth/") ||    // OAuth接口由OAuthController自行处理认证
               path.startsWith("/api/stats/");  // 统计接口允许无认证访问（只读数据）
    }
    
    private boolean checkClientRestriction(ApiKey keyInfo, ServerHttpRequest request) {
        if (!keyInfo.hasClientRestriction()) {
            return true;
        }
        
        String userAgent = request.getHeaders().getFirst("User-Agent");
        if (userAgent == null) {
            return false;
        }
        
        return keyInfo.getAllowedClients().stream()
            .anyMatch(client -> userAgent.toLowerCase().contains(client.toLowerCase()));
    }
    
    private Mono<Void> handleAuthError(ServerHttpResponse response, String message) {
        // Check if response is already committed to avoid UnsupportedOperationException
        if (response.isCommitted()) {
            log.warn("Response already committed, cannot set auth error: {}", message);
            return Mono.empty();
        }
        
        log.error("!!! Setting 401 response: {} !!!", message);
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        String error = JsonUtil.toJson(Map.of(
            "code", 401,
            "error", "认证失败",
            "message", message,
            "timestamp", Instant.now().toString()
        ));
        
        DataBuffer buffer = response.bufferFactory()
            .wrap(error.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}