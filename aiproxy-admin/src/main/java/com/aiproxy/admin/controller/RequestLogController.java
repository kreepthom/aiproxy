package com.aiproxy.admin.controller;

import com.aiproxy.common.entity.RequestLogEntity;
import com.aiproxy.common.service.RequestLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/request-logs")
@Slf4j
public class RequestLogController {
    
    private final RequestLogService requestLogService;
    
    public RequestLogController(RequestLogService requestLogService) {
        this.requestLogService = requestLogService;
    }
    
    /**
     * 获取所有请求日志（支持搜索）
     */
    @GetMapping
    public Mono<Map<String, Object>> getRequestLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String apiKey,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String endpoint,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime) {
        
        log.info("Fetching request logs with filters: page={}, size={}, apiKey={}, status={}", 
            page, size, apiKey, status);
        
        return requestLogService.getAllLogsWithAccounts()
            .filter(logEntry -> {
                // 应用搜索过滤器
                if (apiKey != null && !apiKey.isEmpty()) {
                    String logApiKey = logEntry.getApiKeyId() != null ? logEntry.getApiKeyId() : "";
                    if (!logApiKey.contains(apiKey)) return false;
                }
                if (accountId != null && !accountId.isEmpty()) {
                    String logAccountId = logEntry.getAccountId() != null ? logEntry.getAccountId() : "";
                    if (!logAccountId.contains(accountId)) return false;
                }
                if (status != null && !status.isEmpty()) {
                    String logStatus = determineStatus(logEntry.getStatusCode());
                    if (!logStatus.equals(status)) return false;
                }
                if (endpoint != null && !endpoint.isEmpty()) {
                    String logEndpoint = logEntry.getEndpoint() != null ? logEntry.getEndpoint() : 
                                        (logEntry.getRequestPath() != null ? logEntry.getRequestPath() : "");
                    if (!logEndpoint.contains(endpoint)) return false;
                }
                if (startTime != null && logEntry.getCreatedAt() != null) {
                    if (logEntry.getCreatedAt().toEpochSecond(java.time.ZoneOffset.UTC) * 1000 < startTime) return false;
                }
                if (endTime != null && logEntry.getCreatedAt() != null) {
                    if (logEntry.getCreatedAt().toEpochSecond(java.time.ZoneOffset.UTC) * 1000 > endTime) return false;
                }
                return true;
            })
            .collectList()
            .map(allLogs -> {
                Map<String, Object> result = new HashMap<>();
                int total = allLogs.size();
                
                // 分页
                int fromIndex = page * size;
                int toIndex = Math.min(fromIndex + size, total);
                
                List<Map<String, Object>> pagedLogs;
                if (fromIndex >= total) {
                    // 如果起始索引超过总数，返回空列表
                    pagedLogs = new java.util.ArrayList<>();
                } else {
                    pagedLogs = allLogs.subList(fromIndex, toIndex)
                        .stream()
                        .map(this::convertToResponseMap)
                        .collect(java.util.stream.Collectors.toList());
                }
                
                result.put("data", pagedLogs);
                result.put("total", total);
                result.put("page", page);
                result.put("size", size);
                
                return result;
            })
            .doOnError(error -> log.error("Error fetching request logs", error));
    }
    
    /**
     * 将RequestLogEntity转换为前端需要的格式
     */
    private Map<String, Object> convertToResponseMap(RequestLogEntity entity) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", entity.getId() != null ? entity.getId().toString() : "");
        map.put("apiKey", entity.getApiKeyDisplay() != null ? entity.getApiKeyDisplay() : 
                          (entity.getApiKeyId() != null ? "sk-..." + entity.getApiKeyId().substring(0, 4) : ""));
        map.put("accountId", entity.getAccountId());
        
        // 使用accountEmail字段，这个字段已经在保存日志时设置了
        String email = entity.getAccountEmail();
        if (email == null || email.isEmpty()) {
            // 如果没有email，显示账号ID的一部分
            email = entity.getAccountId() != null ? 
                entity.getAccountId().substring(0, Math.min(8, entity.getAccountId().length())) + "..." : "unknown";
        }
        map.put("accountEmail", email);
        
        map.put("endpoint", entity.getEndpoint() != null ? entity.getEndpoint() : entity.getRequestPath());
        map.put("method", entity.getMethod() != null ? entity.getMethod() : "POST");
        map.put("status", entity.getStatus() != null ? entity.getStatus() : 
                         determineStatus(entity.getStatusCode()));
        map.put("statusCode", entity.getStatusCode() != null ? entity.getStatusCode() : 0);
        map.put("responseTime", entity.getLatencyMs() != null ? entity.getLatencyMs() : 0);
        map.put("tokensUsed", entity.getTotalTokens() != null ? entity.getTotalTokens() : 0);
        map.put("errorMessage", entity.getErrorMessage());
        map.put("timestamp", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : "");
        map.put("requestSize", entity.getRequestSize() != null ? entity.getRequestSize() : 0);
        map.put("responseSize", entity.getResponseSize() != null ? entity.getResponseSize() : 0);
        map.put("model", entity.getModel());
        map.put("provider", entity.getProvider());
        map.put("errorType", entity.getErrorType());
        map.put("clientIp", entity.getClientIp());
        map.put("userAgent", entity.getUserAgent());
        map.put("requestId", entity.getRequestId());
        
        // 重试相关字段
        map.put("retryCount", entity.getRetryCount() != null ? entity.getRetryCount() : 0);
        map.put("failedAccounts", entity.getFailedAccounts());
        map.put("finalAccount", entity.getFinalAccount());
        
        return map;
    }
    
    private String determineStatus(Integer statusCode) {
        if (statusCode == null) return "pending";
        if (statusCode >= 200 && statusCode < 300) return "success";
        return "failed";
    }
    
    /**
     * 导出请求日志
     */
    @GetMapping("/export")
    public Mono<String> exportLogs(
            @RequestParam(required = false) String type,  // all, failed, slow, success
            @RequestParam(required = false) Integer threshold) { // 慢请求阈值（毫秒）
        
        log.info("Exporting logs: type={}, threshold={}", type, threshold);
        
        return requestLogService.getAllLogsWithAccounts()
            .filter(log -> {
                if (type == null || "all".equals(type)) {
                    return true;
                } else if ("failed".equals(type)) {
                    return log.getStatusCode() == null || log.getStatusCode() >= 400;
                } else if ("success".equals(type)) {
                    return log.getStatusCode() != null && log.getStatusCode() >= 200 && log.getStatusCode() < 300;
                } else if ("slow".equals(type)) {
                    int slowThreshold = threshold != null ? threshold : 3000; // 默认3秒
                    return log.getLatencyMs() != null && log.getLatencyMs() > slowThreshold;
                }
                return true;
            })
            .map(this::convertToCSVRow)
            .reduce(getCSVHeader(), (csv, row) -> csv + "\n" + row)
            .map(csv -> csv);
    }
    
    /**
     * 获取CSV头部
     */
    private String getCSVHeader() {
        return "时间,API Key,账号,端点,方法,状态,状态码,响应时间(ms),请求Tokens,响应Tokens,总Tokens,错误信息";
    }
    
    /**
     * 转换为CSV行
     */
    private String convertToCSVRow(RequestLogEntity log) {
        return String.format("%s,%s,%s,%s,%s,%s,%d,%d,%d,%d,%d,%s",
            log.getCreatedAt() != null ? log.getCreatedAt().toString() : "",
            log.getApiKeyId() != null ? log.getApiKeyId() : "",
            log.getAccountId() != null ? log.getAccountId() : "",
            log.getEndpoint() != null ? log.getEndpoint() : log.getRequestPath(),
            log.getMethod() != null ? log.getMethod() : "POST",
            determineStatus(log.getStatusCode()),
            log.getStatusCode() != null ? log.getStatusCode() : 0,
            log.getLatencyMs() != null ? log.getLatencyMs() : 0,
            log.getRequestTokens() != null ? log.getRequestTokens() : 0,
            log.getResponseTokens() != null ? log.getResponseTokens() : 0,
            log.getTotalTokens() != null ? log.getTotalTokens() : 0,
            log.getErrorMessage() != null ? log.getErrorMessage().replace(",", ";") : ""
        );
    }
    
    /**
     * 获取统计信息
     */
    @GetMapping("/stats")
    public Mono<Map<String, Object>> getStats() {
        // TODO: 实现统计信息
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRequests", 0);
        stats.put("successRate", 0.0);
        stats.put("averageLatency", 0);
        stats.put("totalTokens", 0);
        
        return Mono.just(stats);
    }
}