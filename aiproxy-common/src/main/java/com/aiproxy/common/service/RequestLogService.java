package com.aiproxy.common.service;

import com.aiproxy.common.entity.RequestLogEntity;
import com.aiproxy.common.repository.RequestLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class RequestLogService {
    
    private final RequestLogRepository requestLogRepository;
    
    public RequestLogService(RequestLogRepository requestLogRepository) {
        this.requestLogRepository = requestLogRepository;
    }
    
    /**
     * 记录请求日志（新版本，支持更多字段）
     */
    public Mono<Void> logRequestV2(RequestLogEntity logEntity) {
        return Mono.fromRunnable(() -> {
            try {
                // 生成请求ID
                if (logEntity.getRequestId() == null) {
                    logEntity.setRequestId(UUID.randomUUID().toString().substring(0, 8));
                }
                
                // 设置状态
                if (logEntity.getStatus() == null) {
                    logEntity.setStatus(determineStatus(logEntity.getStatusCode()));
                }
                
                // 设置错误类型
                if (logEntity.getStatusCode() != null && logEntity.getStatusCode() >= 400) {
                    logEntity.setErrorType(mapErrorType(logEntity.getStatusCode()));
                }
                
                // 计算总tokens
                if (logEntity.getRequestTokens() != null && logEntity.getResponseTokens() != null) {
                    logEntity.setTotalTokens(logEntity.getRequestTokens() + logEntity.getResponseTokens());
                }
                
                requestLogRepository.save(logEntity);
                log.debug("Request log saved: requestId={}, status={}, latency={}ms", 
                    logEntity.getRequestId(), logEntity.getStatus(), logEntity.getLatencyMs());
            } catch (Exception e) {
                log.error("Failed to save request log", e);
            }
        })
        .subscribeOn(Schedulers.boundedElastic())
        .then();
    }
    
    public Mono<RequestLogEntity> logRequest(String apiKeyId, 
                                            String accountId,
                                            String provider,
                                            String model,
                                            Integer requestTokens,
                                            Integer responseTokens,
                                            Integer latencyMs,
                                            Integer statusCode,
                                            String errorMessage,
                                            String requestPath,
                                            String clientIp) {
        
        return logRequest(apiKeyId, accountId, null, provider, model, requestTokens, 
                         responseTokens, latencyMs, statusCode, errorMessage, requestPath, clientIp);
    }
    
    public Mono<RequestLogEntity> logRequest(String apiKeyId, 
                                            String accountId,
                                            String accountEmail,
                                            String provider,
                                            String model,
                                            Integer requestTokens,
                                            Integer responseTokens,
                                            Integer latencyMs,
                                            Integer statusCode,
                                            String errorMessage,
                                            String requestPath,
                                            String requestBody) {
        return logRequestWithRetry(apiKeyId, accountId, accountEmail, provider, model,
                                   requestTokens, responseTokens, latencyMs, statusCode,
                                   errorMessage, requestPath, null, null, null, null, requestBody);
    }
    
    public Mono<RequestLogEntity> logRequestWithRetry(String apiKeyId, 
                                            String accountId,
                                            String accountEmail,
                                            String provider,
                                            String model,
                                            Integer requestTokens,
                                            Integer responseTokens,
                                            Integer latencyMs,
                                            Integer statusCode,
                                            String errorMessage,
                                            String requestPath,
                                            String clientIp,
                                            Integer retryCount,
                                            String failedAccounts,
                                            String finalAccount,
                                            String requestBody) {
        
        return Mono.fromCallable(() -> {
            RequestLogEntity log = new RequestLogEntity();
            log.setApiKeyId(apiKeyId);
            log.setAccountId(accountId);
            log.setAccountEmail(accountEmail);
            log.setProvider(provider);
            log.setModel(model);
            log.setRequestTokens(requestTokens);
            log.setResponseTokens(responseTokens);
            log.setTotalTokens((requestTokens != null ? requestTokens : 0) + 
                              (responseTokens != null ? responseTokens : 0));
            log.setLatencyMs(latencyMs);
            log.setStatusCode(statusCode);
            log.setErrorMessage(errorMessage);
            log.setRequestPath(requestPath);
            log.setClientIp(clientIp);
            log.setCreatedAt(LocalDateTime.now());
            
            // 新增字段
            log.setStatus(determineStatus(statusCode));
            if (statusCode != null && statusCode >= 400) {
                log.setErrorType(mapErrorType(statusCode));
            }
            
            // 重试相关字段
            log.setRetryCount(retryCount);
            log.setFailedAccounts(failedAccounts);
            log.setFinalAccount(finalAccount);
            
            // 存储请求体（用于错误排查）
            if (requestBody != null && requestBody.length() > 0) {
                // 限制请求体大小，避免存储过大的数据
                String truncatedBody = requestBody.length() > 5000 ? 
                    requestBody.substring(0, 5000) + "...[截断]" : requestBody;
                log.setRequestBody(truncatedBody);
            }
            
            return requestLogRepository.save(log);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .doOnSuccess(savedLog -> log.debug("Request logged: apiKey={}, account={}, model={}, tokens={}", 
            apiKeyId, accountId, model, savedLog.getTotalTokens()))
        .doOnError(error -> log.error("Failed to log request: ", error))
        .onErrorResume(error -> Mono.empty()); // Don't fail the main request if logging fails
    }
    
    /**
     * 根据状态码判断请求状态
     */
    private String determineStatus(Integer statusCode) {
        if (statusCode == null) {
            return "pending";
        } else if (statusCode >= 200 && statusCode < 300) {
            return "success";
        } else {
            return "failed";
        }
    }
    
    /**
     * 根据Claude API错误码映射错误类型
     */
    private String mapErrorType(Integer statusCode) {
        return switch (statusCode) {
            case 400 -> "invalid_request_error";
            case 401 -> "authentication_error";
            case 403 -> "permission_error";
            case 404 -> "not_found_error";
            case 413 -> "request_too_large";
            case 429 -> "rate_limit_error";
            case 500 -> "api_error";
            case 529 -> "overloaded_error";
            default -> statusCode >= 500 ? "server_error" : "client_error";
        };
    }
    
    /**
     * 查询请求日志
     */
    public Mono<Page<RequestLogEntity>> queryLogs(int page, int size) {
        return Mono.fromCallable(() -> {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            return requestLogRepository.findAll(pageable);
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * 获取所有请求日志（Flux）
     */
    public Flux<RequestLogEntity> getAllLogs() {
        return Flux.fromIterable(requestLogRepository.findAll(
            Sort.by(Sort.Direction.DESC, "createdAt")))
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * 获取所有请求日志并加载关联的账户信息
     */
    public Flux<RequestLogEntity> getAllLogsWithAccounts() {
        return Mono.fromCallable(() -> {
            // 使用@Transactional确保在同一个Session中执行
            List<RequestLogEntity> logs = requestLogRepository.findAll(
                Sort.by(Sort.Direction.DESC, "createdAt"));
            
            // 直接返回，不尝试访问懒加载的账户信息
            // 账户邮箱已经存储在account_email字段中
            return logs;
        })
        .flatMapMany(Flux::fromIterable)
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    public Mono<Long> getTotalTokensForApiKeyToday(String apiKeyId) {
        return Mono.fromCallable(() -> {
            LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            Long tokens = requestLogRepository.getTotalTokensByApiKeySince(apiKeyId, startOfDay);
            return tokens != null ? tokens : 0L;
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    public Mono<Long> getTotalRequestsForApiKeyLastHour(String apiKeyId) {
        return Mono.fromCallable(() -> {
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            Long requests = requestLogRepository.getTotalRequestsByApiKeySince(apiKeyId, oneHourAgo);
            return requests != null ? requests : 0L;
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
}