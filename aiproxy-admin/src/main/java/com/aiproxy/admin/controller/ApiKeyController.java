package com.aiproxy.admin.controller;

import com.aiproxy.auth.service.ApiKeyService;
import com.aiproxy.common.model.ApiKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/admin/api-keys")
@Slf4j
public class ApiKeyController {
    
    private final ApiKeyService apiKeyService;
    
    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }
    
    @PostMapping
    public Mono<Map<String, Object>> createApiKey(@RequestBody Map<String, String> request) {
        String name = request.getOrDefault("name", "New API Key");
        String description = request.getOrDefault("description", "");
        
        return apiKeyService.createApiKey(name, description)
            .map(key -> Map.<String, Object>of(
                "success", true,
                "api_key", key,
                "message", "API key created successfully"
            ))
            .onErrorResume(error -> {
                log.warn("Failed to create API key: {}", error.getMessage());
                return Mono.just(Map.<String, Object>of(
                    "success", false,
                    "error", error.getMessage()
                ));
            })
            .doOnNext(result -> {
                if ((Boolean) result.get("success")) {
                    log.info("Successfully created API key: {}", name);
                }
            });
    }
    
    @GetMapping
    public Flux<ApiKey> getAllApiKeys() {
        return apiKeyService.getAllApiKeys()
            .doOnSubscribe(s -> log.info("Getting all API keys"))
            .doOnNext(key -> log.debug("Found API key: {}", key.getName()));
    }
    
    @GetMapping("/{key}")
    public Mono<ApiKey> getApiKey(@PathVariable String key) {
        return apiKeyService.getApiKey(key);
    }
    
    @DeleteMapping("/{key}")
    public Mono<Map<String, Object>> deleteApiKey(@PathVariable String key) {
        return apiKeyService.deleteApiKey(key)
            .map(success -> Map.of(
                "success", success,
                "message", success ? "API key deleted" : "API key not found"
            ));
    }
    
    @DeleteMapping("/id/{id}")
    public Mono<Map<String, Object>> deleteApiKeyById(@PathVariable String id) {
        return apiKeyService.deleteApiKeyById(id)
            .map(success -> Map.of(
                "success", success,
                "message", success ? "API key deleted" : "API key not found"
            ));
    }
}