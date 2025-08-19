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
@Table(name = "api_keys", indexes = {
    @Index(name = "idx_key_hash", columnList = "keyHash", unique = true),
    @Index(name = "idx_is_active", columnList = "isActive")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyEntity {
    
    @Id
    @Column(length = 36)
    private String id;
    
    @Column(nullable = false, unique = true)
    private String keyHash;
    
    @Column(length = 100)
    private String name;
    
    @Column(length = 100)
    private String createdBy;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer rateLimit = 1000;
    
    @Column(nullable = false)
    @Builder.Default
    private Long dailyTokenLimit = 1000000L;
    
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
    private String allowedModels; // JSON array of allowed models
    
    @Column(columnDefinition = "TEXT")
    private String metadata; // JSON string for additional configuration
}