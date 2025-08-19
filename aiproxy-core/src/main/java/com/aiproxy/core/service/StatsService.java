package com.aiproxy.core.service;

import com.aiproxy.auth.service.AccountService;
import com.aiproxy.auth.service.ApiKeyService;
import com.aiproxy.common.model.ClaudeAccount;
import com.aiproxy.common.repository.RequestLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class StatsService {
    
    private final AccountService accountService;
    private final ApiKeyService apiKeyService;
    private final RequestLogRepository requestLogRepository;
    
    // 实时统计数据存储
    private final Map<String, AtomicLong> todayStats = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> totalStats = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> modelUsageStats = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> tokenUsageTrend = new ConcurrentHashMap<>();
    
    // 初始化统计键
    {
        todayStats.put("requests", new AtomicLong(0));
        todayStats.put("tokens", new AtomicLong(0));
        todayStats.put("errors", new AtomicLong(0));
        todayStats.put("rpm", new AtomicLong(0));
        todayStats.put("tpm", new AtomicLong(0));
        
        totalStats.put("requests", new AtomicLong(0));
        totalStats.put("tokens", new AtomicLong(0));
        totalStats.put("errors", new AtomicLong(0));
    }
    
    /**
     * 获取系统概览统计数据
     */
    public Mono<Map<String, Object>> getOverviewStats() {
        return Mono.zip(
            accountService.getAllAccounts().collectList(),  // 从OAuth服务获取账号
            apiKeyService.getAllApiKeys().collectList(),    // 获取所有API Keys
            getRequestLogsStats()                           // 从request_logs表获取统计
        ).map(tuple -> {
            var claudeAccounts = tuple.getT1();
            var apiKeys = tuple.getT2();
            var logsStats = tuple.getT3();
            
            Map<String, Object> stats = new HashMap<>();
            
            // API Keys 统计 - 从实际数据获取
            stats.put("totalKeys", apiKeys.size());
            stats.put("activeKeys", apiKeys.stream()
                .mapToInt(key -> key.isEnabled() ? 1 : 0)
                .sum());
            
            // OAuth账户统计
            stats.put("totalAccounts", claudeAccounts.size());
            stats.put("activeAccounts", claudeAccounts.stream()
                .filter(acc -> "ACTIVE".equals(acc.getStatus()))
                .count());
            
            // 账户池健康度
            double healthPercentage = claudeAccounts.isEmpty() ? 0 : 
                (claudeAccounts.stream().filter(acc -> "ACTIVE".equals(acc.getStatus())).count() * 100.0) / claudeAccounts.size();
            stats.put("accountPoolHealth", healthPercentage);
            
            // 今日统计 - 从request_logs获取
            stats.put("todayRequests", logsStats.getOrDefault("todayRequests", 0L));
            stats.put("todayTokens", logsStats.getOrDefault("todayTokens", 0L));
            stats.put("todayErrors", logsStats.getOrDefault("todayErrors", 0L));
            
            // 总统计 - 从request_logs获取
            stats.put("totalRequests", logsStats.getOrDefault("totalRequests", 0L));
            stats.put("totalTokensUsed", logsStats.getOrDefault("totalTokens", 0L));
            
            // 成功率
            long totalReqs = (Long) logsStats.getOrDefault("totalRequests", 0L);
            long totalErrs = (Long) logsStats.getOrDefault("totalErrors", 0L);
            double successRate = totalReqs > 0 ? ((totalReqs - totalErrs) * 100.0) / totalReqs : 100.0;
            stats.put("successRate", String.format("%.2f", successRate));
            
            // 实时RPM和TPM
            stats.put("currentRPM", todayStats.get("rpm").get());
            stats.put("currentTPM", todayStats.get("tpm").get());
            
            // 平均响应时间（模拟数据，实际应该从请求日志计算）
            stats.put("avgResponseTime", 1230);
            
            return stats;
        });
    }
    
    /**
     * 获取Token使用趋势数据（最近7天）
     */
    public Mono<List<Map<String, Object>>> getTokenUsageTrend() {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> trend = new ArrayList<>();
            LocalDate today = LocalDate.now();
            
            for (int i = 6; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                Map<String, Object> data = new HashMap<>();
                data.put("date", date.toString());
                
                // 实际应该从数据库查询历史数据
                // 这里暂时生成示例数据
                data.put("tokens", (long) (Math.random() * 50000) + 30000);
                data.put("requests", (long) (Math.random() * 1000) + 500);
                
                trend.add(data);
            }
            
            return trend;
        });
    }
    
    /**
     * 获取模型使用分布数据
     */
    public Mono<List<Map<String, Object>>> getModelUsageDistribution() {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> distribution = new ArrayList<>();
            
            // 实际应该从请求日志统计
            // 这里返回示例数据
            Map<String, Object> opus41 = new HashMap<>();
            opus41.put("name", "Claude Opus 4.1");
            opus41.put("value", 40);
            opus41.put("color", "#8884d8");
            distribution.add(opus41);
            
            Map<String, Object> sonnet4 = new HashMap<>();
            sonnet4.put("name", "Claude Sonnet 4");
            sonnet4.put("value", 25);
            sonnet4.put("color", "#82ca9d");
            distribution.add(sonnet4);
            
            Map<String, Object> sonnet37 = new HashMap<>();
            sonnet37.put("name", "Claude Sonnet 3.7");
            sonnet37.put("value", 20);
            sonnet37.put("color", "#ffc658");
            distribution.add(sonnet37);
            
            Map<String, Object> haiku35 = new HashMap<>();
            haiku35.put("name", "Claude Haiku 3.5");
            haiku35.put("value", 15);
            haiku35.put("color", "#ff7c7c");
            distribution.add(haiku35);
            
            return distribution;
        });
    }
    
    /**
     * 获取API Keys使用趋势
     */
    public Mono<List<Map<String, Object>>> getApiKeysUsageTrend() {
        return apiKeyService.getAllApiKeys().collectList()
            .map(apiKeys -> {
                List<Map<String, Object>> usageTrend = new ArrayList<>();
                
                for (var apiKey : apiKeys) {
                    Map<String, Object> keyUsage = new HashMap<>();
                    keyUsage.put("key", maskApiKey(apiKey.getKey()));
                    keyUsage.put("usage", apiKey.getTotalRequests() != null ? apiKey.getTotalRequests() : 0L);
                    keyUsage.put("name", apiKey.getName());
                    keyUsage.put("enabled", apiKey.isEnabled());
                    usageTrend.add(keyUsage);
                }
                
                return usageTrend;
            });
    }
    
    /**
     * 记录请求
     */
    public void recordRequest(String model, long tokens, boolean success) {
        // 更新今日统计
        todayStats.get("requests").incrementAndGet();
        todayStats.get("tokens").addAndGet(tokens);
        if (!success) {
            todayStats.get("errors").incrementAndGet();
        }
        
        // 更新总统计
        totalStats.get("requests").incrementAndGet();
        totalStats.get("tokens").addAndGet(tokens);
        if (!success) {
            totalStats.get("errors").incrementAndGet();
        }
        
        // 更新模型使用统计
        modelUsageStats.computeIfAbsent(model, k -> new ConcurrentHashMap<>())
            .merge("count", 1L, Long::sum);
    }
    
    /**
     * 更新实时RPM/TPM
     */
    public void updateRealTimeMetrics(long rpm, long tpm) {
        todayStats.get("rpm").set(rpm);
        todayStats.get("tpm").set(tpm);
    }
    
    /**
     * 重置今日统计（每天凌晨调用）
     */
    public void resetDailyStats() {
        todayStats.get("requests").set(0);
        todayStats.get("tokens").set(0);
        todayStats.get("errors").set(0);
    }
    
    private String maskApiKey(String key) {
        if (key == null || key.length() < 8) {
            return "sk-...";
        }
        return key.substring(0, 3) + "..." + key.substring(key.length() - 3);
    }
    
    /**
     * 从request_logs表获取统计数据
     */
    private Mono<Map<String, Object>> getRequestLogsStats() {
        return Mono.fromCallable(() -> {
            Map<String, Object> stats = new HashMap<>();
            LocalDate today = LocalDate.now();
            LocalDateTime todayStart = today.atStartOfDay();
            
            try {
                // 获取总统计
                Long totalRequests = requestLogRepository.countByStatusCodeBetween(200, 299);
                Long totalErrors = requestLogRepository.countByStatusCodeGreaterThanEqual(400);
                Long totalTokens = requestLogRepository.sumTotalTokens();
                
                // 获取今日统计
                Long todayRequests = requestLogRepository.countByCreatedAtAfterAndStatusCodeBetween(
                    todayStart, 200, 299);
                Long todayErrors = requestLogRepository.countByCreatedAtAfterAndStatusCodeGreaterThanEqual(
                    todayStart, 400);
                Long todayTokens = requestLogRepository.sumTotalTokensByCreatedAtAfter(todayStart);
                
                stats.put("totalRequests", totalRequests != null ? totalRequests : 0L);
                stats.put("totalErrors", totalErrors != null ? totalErrors : 0L);
                stats.put("totalTokens", totalTokens != null ? totalTokens : 0L);
                stats.put("todayRequests", todayRequests != null ? todayRequests : 0L);
                stats.put("todayErrors", todayErrors != null ? todayErrors : 0L);
                stats.put("todayTokens", todayTokens != null ? todayTokens : 0L);
                
            } catch (Exception e) {
                log.error("Error fetching request logs stats:", e);
                // 返回默认值
                stats.put("totalRequests", 0L);
                stats.put("totalErrors", 0L);
                stats.put("totalTokens", 0L);
                stats.put("todayRequests", 0L);
                stats.put("todayErrors", 0L);
                stats.put("todayTokens", 0L);
            }
            
            return stats;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}