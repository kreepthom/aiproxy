package com.aiproxy.core.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {
    
    @GetMapping("/health")
    public Mono<Map<String, Object>> health() {
        return Mono.just(Map.of(
            "status", "UP",
            "timestamp", Instant.now().toString(),
            "service", "claude-relay-spring"
        ));
    }
    
    @GetMapping("/health/ready")
    public Mono<Map<String, Object>> ready() {
        return Mono.just(Map.of(
            "status", "READY",
            "timestamp", Instant.now().toString()
        ));
    }
    
    @GetMapping("/health/live")
    public Mono<Map<String, Object>> live() {
        return Mono.just(Map.of(
            "status", "LIVE",
            "timestamp", Instant.now().toString()
        ));
    }
}