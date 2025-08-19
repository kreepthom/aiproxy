package com.aiproxy.core.controller;

import com.aiproxy.common.model.ApiKey;
import com.aiproxy.core.service.ProxyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Slf4j
public class ClaudeRelayController {
    
    private final ProxyService proxyService;
    
    public ClaudeRelayController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }
    
    @PostMapping(value = "/messages")
    public Mono<ResponseEntity<?>> relayMessages(
            @RequestBody Map<String, Object> request,
            ServerWebExchange exchange) {
        
        ApiKey apiKey = exchange.getAttribute("apiKey");
        boolean stream = (boolean) request.getOrDefault("stream", false);
        
        log.info("Relaying message request - Stream: {}, Model: {}", 
            stream, request.get("model"));
        
        if (stream) {
            Flux<ServerSentEvent<String>> eventStream = proxyService.relayStreamRequest(request, apiKey);
            return Mono.just(ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(eventStream));
        } else {
            return proxyService.relayNormalRequest(request, apiKey)
                    .map(result -> ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(result));
        }
    }
    
    @PostMapping("/complete")
    public Mono<Map<String, Object>> relayComplete(
            @RequestBody Map<String, Object> request,
            ServerWebExchange exchange) {
        
        ApiKey apiKey = exchange.getAttribute("apiKey");
        log.info("Relaying complete request - Model: {}", request.get("model"));
        
        return proxyService.relayCompleteRequest(request, apiKey);
    }
    
    @GetMapping("/models")
    public Mono<Map<String, Object>> getModels(ServerWebExchange exchange) {
        ApiKey apiKey = exchange.getAttribute("apiKey");
        return proxyService.getAvailableModels(apiKey);
    }
}