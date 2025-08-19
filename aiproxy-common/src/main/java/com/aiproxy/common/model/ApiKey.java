package com.aiproxy.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey implements Serializable {
    
    private String id;
    private String key;
    private String name;
    private String description;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime expiresAt;
    private List<String> allowedClients;
    private RateLimitRule rateLimitRule;
    private Long totalRequests;
    private Long totalTokens;
    
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }
    
    public boolean isValid() {
        return enabled && !isExpired();
    }
    
    public boolean hasClientRestriction() {
        return allowedClients != null && !allowedClients.isEmpty();
    }
}