package com.aiproxy.common.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "accounts", indexes = {
    @Index(name = "idx_enabled_status", columnList = "enabled,status"),
    @Index(name = "idx_provider", columnList = "provider")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountEntity {
    
    @Id
    @Column(length = 36)
    private String id;
    
    @Column(nullable = false)
    private String email;
    
    @Column(length = 20, nullable = false)
    private String provider; // CLAUDE, OPENAI, GEMINI
    
    @Column(columnDefinition = "TEXT")
    private String accessToken;
    
    @Column(columnDefinition = "TEXT")
    private String refreshToken;
    
    private LocalDateTime tokenExpiresAt;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;
    
    @Column(length = 20)
    @Builder.Default
    private String status = "ACTIVE";
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    private LocalDateTime lastUsedAt;
    
    @Column(nullable = false)
    @Builder.Default
    private Long totalRequests = 0L;
    
    @Column(nullable = false)
    @Builder.Default
    private Long totalTokens = 0L;
    
    @Column(columnDefinition = "TEXT")
    private String metadata; // JSON string for additional data
}