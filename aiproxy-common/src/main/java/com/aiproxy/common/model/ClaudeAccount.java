package com.aiproxy.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaudeAccount {
    
    private String id;
    private String email;
    private String accessToken;
    private String refreshToken;
    private LocalDateTime tokenExpiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    private boolean enabled;
    private Long totalRequests;
    private Long totalTokens;
    private String status; // ACTIVE, EXPIRED, RATE_LIMITED, DISABLED
    
    public boolean isTokenExpired() {
        return tokenExpiresAt != null && LocalDateTime.now().isAfter(tokenExpiresAt);
    }
    
    public boolean isActive() {
        return enabled && "ACTIVE".equals(status) && !isTokenExpired();
    }
}