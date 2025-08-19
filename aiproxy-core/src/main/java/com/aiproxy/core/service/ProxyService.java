package com.aiproxy.core.service;

import com.aiproxy.common.constants.ApiConstants;
import com.aiproxy.common.constants.ClaudeErrorCode;
import com.aiproxy.common.exception.RelayException;
import com.aiproxy.common.model.Account;
import com.aiproxy.common.model.ApiKey;
import com.aiproxy.common.service.RequestLogService;
import com.aiproxy.common.utils.ErrorClassifier;
import com.aiproxy.common.utils.JsonUtil;
import com.aiproxy.core.config.AccountPoolConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class ProxyService {
    
    private final WebClient claudeWebClient;
    private final AccountPoolService accountPoolService;
    private final AccountPoolConfig accountPoolConfig;
    private final RequestLogService requestLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public ProxyService(WebClient claudeWebClient, 
                       AccountPoolService accountPoolService, 
                       AccountPoolConfig accountPoolConfig,
                       RequestLogService requestLogService) {
        this.claudeWebClient = claudeWebClient;
        this.accountPoolService = accountPoolService;
        this.accountPoolConfig = accountPoolConfig;
        this.requestLogService = requestLogService;
    }
    
    public Flux<ServerSentEvent<String>> relayStreamRequest(Map<String, Object> request, ApiKey apiKey) {
        // Inject Claude Code system prompt for OAuth tokens
        Map<String, Object> modifiedRequest = injectClaudeCodeSystemPrompt(request);
        return relayStreamRequestWithRetry(modifiedRequest, apiKey, new HashSet<>(), 0);
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> injectClaudeCodeSystemPrompt(Map<String, Object> request) {
        // Create a copy of the request to avoid modifying the original
        Map<String, Object> modifiedRequest = new HashMap<>(request);
        
        // Claude Code system prompt - MUST be exact match!
        String claudeCodePrompt = "You are Claude Code, Anthropic's official CLI for Claude.";
        
        // Check if there's already a system parameter
        Object existingSystem = modifiedRequest.get("system");
        
        if (existingSystem != null) {
            // Prepend to existing system message
            if (existingSystem instanceof String) {
                modifiedRequest.put("system", claudeCodePrompt + "\n\n" + existingSystem);
            } else if (existingSystem instanceof List) {
                List<Map<String, Object>> systemList = new ArrayList<>((List<Map<String, Object>>) existingSystem);
                // Add Claude Code prompt as first content item
                Map<String, Object> claudeCodeContent = new HashMap<>();
                claudeCodeContent.put("type", "text");
                claudeCodeContent.put("text", claudeCodePrompt);
                
                // Add cache control for the Claude Code prompt
                Map<String, Object> cacheControl = new HashMap<>();
                cacheControl.put("type", "ephemeral");
                claudeCodeContent.put("cache_control", cacheControl);
                
                systemList.add(0, claudeCodeContent);
                modifiedRequest.put("system", systemList);
            }
        } else {
            // Add new system parameter with Claude Code prompt
            List<Map<String, Object>> systemList = new ArrayList<>();
            Map<String, Object> claudeCodeContent = new HashMap<>();
            claudeCodeContent.put("type", "text");
            claudeCodeContent.put("text", claudeCodePrompt);
            
            Map<String, Object> cacheControl = new HashMap<>();
            cacheControl.put("type", "ephemeral");
            claudeCodeContent.put("cache_control", cacheControl);
            
            systemList.add(claudeCodeContent);
            modifiedRequest.put("system", systemList);
        }
        
        return modifiedRequest;
    }
    
    private Flux<ServerSentEvent<String>> relayStreamRequestWithRetry(Map<String, Object> request, ApiKey apiKey, Set<String> triedAccounts, int attempt) {
        final int maxRetries = accountPoolConfig.getEffectiveMaxRetries();
        
        log.info("=== RELAY STREAM REQUEST START ===");
        log.info("API Key ID: {}", apiKey.getId());
        log.info("Request Model: {}", request.get("model"));
        log.info("Request Messages: {}", JsonUtil.toJson(request.get("messages")));
        log.info("Attempt: {}/{}, Tried accounts: {}", attempt + 1, maxRetries, triedAccounts.size());
        
        // If retry is disabled or we've reached max attempts
        if (!accountPoolConfig.isEnableRetry() || attempt >= maxRetries) {
            log.error("All {} accounts failed for stream request", triedAccounts.size());
            return Flux.just(ServerSentEvent.<String>builder()
                .event("error")
                .data(buildErrorResponse(new RelayException("All available accounts failed")))
                .build());
        }
        
        return accountPoolService.selectAccountWithExclusions(triedAccounts)
            .flatMapMany(account -> {
                log.info("=== SELECTED ACCOUNT ===");
                log.info("Attempt {}: Using account: {}", attempt + 1, account.getId());
                log.info("Account email: {}", account.getEmail());
                log.info("Account status: {}", account.getStatus());
                triedAccounts.add(account.getId());
                
                Instant startTime = Instant.now();
                String model = (String) request.get("model");
                
                log.info("=== SENDING REQUEST TO CLAUDE ===");
                log.info("URL: {} {}", ApiConstants.CLAUDE_BASE_URL, "/v1/messages");
                log.info("Request Body: {}", JsonUtil.toJson(request));
                
                return claudeWebClient.post()
                    .uri("/v1/messages")
                    .headers(headers -> setupHeaders(headers, account))
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), clientResponse -> {
                        int statusCode = clientResponse.statusCode().value();
                        String requestBodyJson = JsonUtil.toJson(request);
                        
                        return clientResponse.bodyToMono(String.class)
                            .flatMap(body -> {
                                String errorType = null;
                                String errorMessage = null;
                                
                                // 尝试解析错误响应
                                try {
                                    JsonNode errorNode = objectMapper.readTree(body);
                                    if (errorNode.has("error")) {
                                        JsonNode error = errorNode.get("error");
                                        errorType = error.has("type") ? error.get("type").asText() : null;
                                        errorMessage = error.has("message") ? error.get("message").asText() : body;
                                    } else {
                                        errorMessage = body;
                                    }
                                } catch (Exception e) {
                                    errorMessage = body;
                                }
                                
                                // 记录详细的错误日志（中文）
                                String detailedError = ClaudeErrorCode.formatErrorMessage(
                                    statusCode, errorType, errorMessage, requestBodyJson
                                );
                                log.error(detailedError);
                                
                                // 保存错误详情到请求日志
                                requestLogService.logRequest(
                                    apiKey.getId(),
                                    account.getId(),
                                    account.getEmail(),
                                    "CLAUDE",
                                    model,
                                    null,
                                    null,
                                    0,
                                    statusCode,
                                    detailedError,
                                    "/v1/messages",
                                    requestBodyJson
                                ).subscribe();
                                
                                return Mono.error(new RelayException(
                                    statusCode + " " + ClaudeErrorCode.getDescription(String.valueOf(statusCode))
                                ));
                            });
                    })
                    .bodyToFlux(String.class)
                    .map(this::parseSSEData)
                    .filter(Objects::nonNull)
                    .map(data -> ServerSentEvent.<String>builder()
                        .id(UUID.randomUUID().toString())
                        .event(getEventType(data))
                        .data(data)
                        .build())
                    .doOnComplete(() -> {
                        log.info("Stream completed successfully with account: {}", account.getId());
                        accountPoolService.markAccountSuccess(account.getId());
                        
                        // Log successful request with retry information
                        long latency = Duration.between(startTime, Instant.now()).toMillis();
                        
                        requestLogService.logRequest(
                            apiKey.getId(),
                            account.getId(),
                            account.getEmail(),
                            "CLAUDE",
                            model,
                            null, // Request tokens will be calculated from usage
                            null, // Response tokens will be calculated from usage
                            (int) latency,
                            200,
                            null,
                            "/v1/messages",
                            null // Client IP will be extracted from context
                        ).subscribe();
                    })
                    .onErrorResume(error -> {
                        accountPoolService.markAccountFailed(account.getId(), error);
                        
                        // Log failed request
                        long latency = Duration.between(startTime, Instant.now()).toMillis();
                        
                        // Get status code from error if available
                        int statusCode = 500;
                        if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                            statusCode = ((org.springframework.web.reactive.function.client.WebClientResponseException) error).getStatusCode().value();
                        }
                        
                        // 记录失败请求时包含请求体
                        String requestBodyJson = JsonUtil.toJson(request);
                        requestLogService.logRequest(
                            apiKey.getId(),
                            account.getId(),
                            account.getEmail(),
                            "CLAUDE",
                            model,
                            null,
                            null,
                            (int) latency,
                            statusCode,
                            error.getMessage(),
                            "/v1/messages",
                            requestBodyJson  // 失败时记录请求体用于调试
                        ).subscribe();
                        
                        // 判断是否应该重试
                        boolean shouldRetry = ErrorClassifier.isRetryableError(error);
                        String errorDesc = ErrorClassifier.getErrorDescription(error);
                        
                        if (!shouldRetry) {
                            log.error("Account {} failed with non-retryable error: {} - {}", 
                                account.getId(), statusCode, errorDesc);
                            return Flux.just(ServerSentEvent.<String>builder()
                                .event("error")
                                .data(buildErrorResponse(error))
                                .build());
                        }
                        
                        // Check if retry is enabled and we haven't exceeded max attempts
                        if (accountPoolConfig.isEnableRetry() && attempt + 1 < accountPoolConfig.getEffectiveMaxRetries()) {
                            log.warn("Account {} failed with retryable error: {} - {}, trying next account... (attempt {}/{})", 
                                account.getId(), statusCode, errorDesc, attempt + 1, accountPoolConfig.getEffectiveMaxRetries());
                            return relayStreamRequestWithRetry(request, apiKey, triedAccounts, attempt + 1);
                        } else {
                            log.error("Account {} failed and no more retries allowed", account.getId());
                            return Flux.just(ServerSentEvent.<String>builder()
                                .event("error")
                                .data(buildErrorResponse(error))
                                .build());
                        }
                    });
            })
            .onErrorResume(error -> {
                if (error.getMessage() != null && error.getMessage().contains("No available accounts")) {
                    log.error("No more accounts available for retry");
                    return Flux.just(ServerSentEvent.<String>builder()
                        .event("error")
                        .data(buildErrorResponse(error))
                        .build());
                }
                return relayStreamRequestWithRetry(request, apiKey, triedAccounts, attempt + 1);
            });
    }
    
    public Mono<String> relayNormalRequest(Map<String, Object> request, ApiKey apiKey) {
        // Inject Claude Code system prompt for OAuth tokens
        Map<String, Object> modifiedRequest = injectClaudeCodeSystemPrompt(request);
        return relayNormalRequestWithRetry(modifiedRequest, apiKey, new HashSet<>(), 0);
    }
    
    private Mono<String> relayNormalRequestWithRetry(Map<String, Object> request, ApiKey apiKey, Set<String> triedAccounts, int attempt) {
        final int maxRetries = accountPoolConfig.getEffectiveMaxRetries();
        
        log.info("=== RELAY NORMAL REQUEST START ===");
        log.info("API Key ID: {}", apiKey.getId());
        log.info("Request Model: {}", request.get("model"));
        log.info("Request Messages: {}", JsonUtil.toJson(request.get("messages")));
        log.info("Attempt: {}/{}, Tried accounts: {}", attempt + 1, maxRetries, triedAccounts.size());
        
        // If retry is disabled or we've reached max attempts
        if (!accountPoolConfig.isEnableRetry() || attempt >= maxRetries) {
            log.error("All {} accounts failed for normal request", triedAccounts.size());
            return Mono.just(buildErrorResponse(new RelayException("All available accounts failed")));
        }
        
        return accountPoolService.selectAccountWithExclusions(triedAccounts)
            .flatMap(account -> {
                log.info("=== SELECTED ACCOUNT (Normal) ===");
                log.info("Attempt {}: Using account: {}", attempt + 1, account.getId());
                log.info("Account email: {}", account.getEmail());
                log.info("Account status: {}", account.getStatus());
                triedAccounts.add(account.getId());
                
                Instant startTime = Instant.now();
                String model = (String) request.get("model");
                
                log.info("=== SENDING NORMAL REQUEST TO CLAUDE ===");
                log.info("URL: {} {}", ApiConstants.CLAUDE_BASE_URL, "/v1/messages");
                log.info("Request Body: {}", JsonUtil.toJson(request));
                
                return claudeWebClient.post()
                    .uri("/v1/messages")
                    .headers(headers -> setupHeaders(headers, account))
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), clientResponse -> {
                        int statusCode = clientResponse.statusCode().value();
                        String requestBodyJson = JsonUtil.toJson(request);
                        
                        return clientResponse.bodyToMono(String.class)
                            .flatMap(body -> {
                                String errorType = null;
                                String errorMessage = null;
                                
                                // 尝试解析错误响应
                                try {
                                    JsonNode errorNode = objectMapper.readTree(body);
                                    if (errorNode.has("error")) {
                                        JsonNode error = errorNode.get("error");
                                        errorType = error.has("type") ? error.get("type").asText() : null;
                                        errorMessage = error.has("message") ? error.get("message").asText() : body;
                                    } else {
                                        errorMessage = body;
                                    }
                                } catch (Exception e) {
                                    errorMessage = body;
                                }
                                
                                // 记录详细的错误日志（中文）
                                String detailedError = ClaudeErrorCode.formatErrorMessage(
                                    statusCode, errorType, errorMessage, requestBodyJson
                                );
                                log.error(detailedError);
                                
                                // 保存错误详情到请求日志
                                requestLogService.logRequest(
                                    apiKey.getId(),
                                    account.getId(),
                                    account.getEmail(),
                                    "CLAUDE",
                                    model,
                                    null,
                                    null,
                                    0,
                                    statusCode,
                                    detailedError,
                                    "/v1/messages",
                                    requestBodyJson
                                ).subscribe();
                                
                                return Mono.error(new org.springframework.web.reactive.function.client.WebClientResponseException(
                                    statusCode,
                                    ClaudeErrorCode.getDescription(String.valueOf(statusCode)),
                                    null, body.getBytes(), null
                                ));
                            });
                    })
                    .bodyToMono(String.class)
                    .doOnSuccess(response -> {
                        log.info("Request completed successfully with account: {}", account.getId());
                        accountPoolService.markAccountSuccess(account.getId());
                        
                        // Log successful request with retry information
                        long latency = Duration.between(startTime, Instant.now()).toMillis();
                        
                        // Extract token usage from response if available
                        Integer requestTokens = null;
                        Integer responseTokens = null;
                        try {
                            JsonNode responseNode = JsonUtil.parseJson(response);
                            if (responseNode.has("usage")) {
                                JsonNode usage = responseNode.get("usage");
                                requestTokens = usage.has("input_tokens") ? usage.get("input_tokens").asInt() : null;
                                responseTokens = usage.has("output_tokens") ? usage.get("output_tokens").asInt() : null;
                            }
                        } catch (Exception e) {
                            log.debug("Failed to parse usage from response", e);
                        }
                        
                        requestLogService.logRequest(
                            apiKey.getId(),
                            account.getId(),
                            account.getEmail(),
                            "CLAUDE",
                            model,
                            requestTokens,
                            responseTokens,
                            (int) latency,
                            200,
                            null,
                            "/v1/messages",
                            null  // 成功请求不记录请求体
                        ).subscribe();
                    })
                    .onErrorResume(error -> {
                        accountPoolService.markAccountFailed(account.getId(), error);
                        
                        // Log failed request
                        long latency = Duration.between(startTime, Instant.now()).toMillis();
                        
                        // Get status code from error if available
                        int statusCode = 500;
                        if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                            statusCode = ((org.springframework.web.reactive.function.client.WebClientResponseException) error).getStatusCode().value();
                        }
                        
                        // 记录失败请求时包含请求体
                        String requestBodyJson = JsonUtil.toJson(request);
                        requestLogService.logRequest(
                            apiKey.getId(),
                            account.getId(),
                            account.getEmail(),
                            "CLAUDE",
                            model,
                            null,
                            null,
                            (int) latency,
                            statusCode,
                            error.getMessage(),
                            "/v1/messages",
                            requestBodyJson  // 失败时记录请求体用于调试
                        ).subscribe();
                        
                        // 判断是否应该重试
                        boolean shouldRetry = ErrorClassifier.isRetryableError(error);
                        String errorDesc = ErrorClassifier.getErrorDescription(error);
                        
                        if (!shouldRetry) {
                            log.error("Account {} failed with non-retryable error: {} - {}", 
                                account.getId(), statusCode, errorDesc);
                            return Mono.just(buildErrorResponse(error));
                        }
                        
                        // Check if retry is enabled and we haven't exceeded max attempts
                        if (accountPoolConfig.isEnableRetry() && attempt + 1 < accountPoolConfig.getEffectiveMaxRetries()) {
                            log.warn("Account {} failed with retryable error: {} - {}, trying next account... (attempt {}/{})", 
                                account.getId(), statusCode, errorDesc, attempt + 1, accountPoolConfig.getEffectiveMaxRetries());
                            return relayNormalRequestWithRetry(request, apiKey, triedAccounts, attempt + 1);
                        } else {
                            log.error("Account {} failed and no more retries allowed", account.getId());
                            return Mono.just(buildErrorResponse(error));
                        }
                    });
            })
            .onErrorResume(error -> {
                if (error.getMessage() != null && error.getMessage().contains("No available accounts")) {
                    log.error("No more accounts available for retry");
                    return Mono.just(buildErrorResponse(error));
                }
                return relayNormalRequestWithRetry(request, apiKey, triedAccounts, attempt + 1);
            });
    }
    
    public Mono<Map<String, Object>> relayCompleteRequest(Map<String, Object> request, ApiKey apiKey) {
        return accountPoolService.selectAccount()
            .flatMap(account -> {
                Instant startTime = Instant.now();
                String model = (String) request.get("model");
                
                return claudeWebClient.post()
                    .uri("/v1/complete")
                    .headers(headers -> setupHeaders(headers, account))
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .doOnSuccess(response -> {
                        accountPoolService.markAccountSuccess(account.getId());
                        
                        // Log successful request
                        long latency = Duration.between(startTime, Instant.now()).toMillis();
                        requestLogService.logRequest(
                            apiKey.getId(),
                            account.getId(),
                            "CLAUDE",
                            model,
                            null,
                            null,
                            (int) latency,
                            200,
                            null,
                            "/v1/complete",
                            null
                        ).subscribe();
                    })
                    .doOnError(error -> {
                        accountPoolService.markAccountFailed(account.getId(), error);
                        
                        // Log failed request
                        long latency = Duration.between(startTime, Instant.now()).toMillis();
                        requestLogService.logRequest(
                            apiKey.getId(),
                            account.getId(),
                            "CLAUDE",
                            model,
                            null,
                            null,
                            (int) latency,
                            500,
                            error.getMessage(),
                            "/v1/complete",
                            null
                        ).subscribe();
                    });
            });
    }
    
    public Mono<Map<String, Object>> getAvailableModels(ApiKey apiKey) {
        return accountPoolService.selectAccount()
            .flatMap(account -> {
                return claudeWebClient.get()
                    .uri("/v1/models")
                    .headers(headers -> setupHeaders(headers, account))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
            })
            .onErrorReturn(Map.of(
                "models", new Object[]{
                    Map.of("id", "claude-3-opus-20240229", "name", "Claude 3 Opus"),
                    Map.of("id", "claude-3-sonnet-20240229", "name", "Claude 3 Sonnet"),
                    Map.of("id", "claude-3-haiku-20240307", "name", "Claude 3 Haiku")
                }
            ));
    }
    
    private void setupHeaders(HttpHeaders headers, Account account) {
        log.info("=== Setting up headers for Claude API ===");
        log.info("Account ID: {}", account.getId());
        log.info("Account Email: {}", account.getEmail());
        log.info("Access Token (first 30 chars): {}", 
            account.getAccessToken().substring(0, Math.min(30, account.getAccessToken().length())) + "...");
        
        // Check token type and use appropriate header
        String token = account.getAccessToken();
        if (token.startsWith("oauth_") || token.startsWith("sk-ant-oat")) {
            // OAuth token - use Bearer authorization
            log.info("Using OAuth token with Bearer authorization");
            headers.setBearerAuth(token);
            
            // Add Claude Code specific beta headers for OAuth
            headers.add("anthropic-beta", "claude-code-20250219,oauth-2025-04-20,interleaved-thinking-2025-05-14,fine-grained-tool-streaming-2025-05-14");
            // OAuth tokens use the anthropic-version
            headers.add(ApiConstants.CLAUDE_VERSION_HEADER, "2023-06-01");
        } else if (token.startsWith("sk-ant-api")) {
            // Standard API key - use x-api-key header and anthropic-version
            log.info("Using standard API key with x-api-key header");
            headers.add("x-api-key", token);
            headers.add(ApiConstants.CLAUDE_VERSION_HEADER, ApiConstants.CLAUDE_VERSION);
        } else {
            // Unknown format, try x-api-key
            log.warn("Unknown token format, trying x-api-key header");
            headers.add("x-api-key", token);
            headers.add(ApiConstants.CLAUDE_VERSION_HEADER, ApiConstants.CLAUDE_VERSION);
        }
        
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("User-Agent", "claude-cli/1.0.57 (external, cli)");
        
        log.info("Headers set: {}, Content-Type={}, User-Agent={}", 
            token.startsWith("oauth_") || token.startsWith("sk-ant-oat") ? "Authorization=Bearer with anthropic-beta" : "x-api-key with anthropic-version",
            MediaType.APPLICATION_JSON,
            "claude-cli/1.0.57 (external, cli)");
    }
    
    private String parseSSEData(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        
        if (raw.startsWith("data: ")) {
            String data = raw.substring(6).trim();
            if (!"[DONE]".equals(data)) {
                return data;
            }
        }
        return raw;
    }
    
    private String getEventType(String data) {
        try {
            JsonNode node = JsonUtil.parseJson(data);
            if (node.has("type")) {
                return node.get("type").asText();
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return "message";
    }
    
    private String buildErrorResponse(Throwable error) {
        return JsonUtil.toJson(Map.of(
            "error", Map.of(
                "message", error.getMessage(),
                "type", "relay_error"
            )
        ));
    }
}