package com.aiproxy.core.controller;

import com.aiproxy.core.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@Slf4j
public class StatsController {
    
    private final StatsService statsService;
    
    @GetMapping("/overview")
    public Mono<Map<String, Object>> getOverviewStats() {
        return statsService.getOverviewStats()
            .doOnSuccess(stats -> log.debug("获取系统概览统计: {}", stats));
    }
    
    @GetMapping("/token-trend")
    public Mono<List<Map<String, Object>>> getTokenUsageTrend(
        @RequestParam(defaultValue = "7") int days
    ) {
        return statsService.getTokenUsageTrend()
            .doOnSuccess(trend -> log.debug("获取Token使用趋势: {} 天", days));
    }
    
    @GetMapping("/model-distribution")
    public Mono<List<Map<String, Object>>> getModelUsageDistribution() {
        return statsService.getModelUsageDistribution()
            .doOnSuccess(dist -> log.debug("获取模型使用分布"));
    }
    
    @GetMapping("/apikeys-usage")
    public Mono<List<Map<String, Object>>> getApiKeysUsageTrend() {
        return statsService.getApiKeysUsageTrend()
            .doOnSuccess(trend -> log.debug("获取API Keys使用趋势"));
    }
    
    @GetMapping("/realtime")
    public Mono<Map<String, Object>> getRealtimeMetrics() {
        return Mono.fromCallable(() -> {
            Map<String, Object> metrics = new HashMap<>();
            
            // 实时数据（实际应该从监控系统获取）
            metrics.put("rpm", 0); // 每分钟请求数
            metrics.put("tpm", 0); // 每分钟Token数
            metrics.put("activeConnections", 0); // 活跃连接数
            metrics.put("queueSize", 0); // 队列大小
            
            return metrics;
        });
    }
    
    @GetMapping("/dashboard")
    public Mono<Map<String, Object>> getDashboardData() {
        return Mono.zip(
            statsService.getOverviewStats(),
            statsService.getTokenUsageTrend(),
            statsService.getModelUsageDistribution(),
            statsService.getApiKeysUsageTrend()
        ).map(tuple -> {
            Map<String, Object> dashboard = new HashMap<>();
            dashboard.put("overview", tuple.getT1());
            dashboard.put("tokenTrend", tuple.getT2());
            dashboard.put("modelDistribution", tuple.getT3());
            dashboard.put("apiKeysUsage", tuple.getT4());
            return dashboard;
        });
    }
}