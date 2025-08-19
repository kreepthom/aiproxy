package com.aiproxy.common.model;

import com.aiproxy.common.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account implements Serializable {
    
    private String id;
    private String name;
    private String email;
    private String accessToken;
    private String refreshToken;
    private AccountStatus status;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    private Long usageCount;
    private ProxyConfig proxyConfig;
    
    public boolean hasProxy() {
        return proxyConfig != null && proxyConfig.isEnabled();
    }
    
    public boolean isExpired() {
        return expireAt != null && expireAt.isBefore(LocalDateTime.now());
    }
    
    public boolean isActive() {
        return status == AccountStatus.ACTIVE && !isExpired();
    }
    
    public boolean isDisabled() {
        return status == AccountStatus.DISABLED || status == AccountStatus.INACTIVE;
    }
}