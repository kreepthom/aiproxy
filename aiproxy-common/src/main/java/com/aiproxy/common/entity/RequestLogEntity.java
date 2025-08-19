package com.aiproxy.common.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "request_logs", indexes = {
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_api_key", columnList = "apiKeyId"),
    @Index(name = "idx_account", columnList = "accountId"),
    @Index(name = "idx_status_code", columnList = "statusCode")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestLogEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(length = 36)
    private String apiKeyId;
    
    @Column(length = 100)
    private String apiKeyDisplay; // 显示用的API Key掩码
    
    @Column(length = 36)
    private String accountId;
    
    @Column(length = 100)
    private String accountEmail; // 账号邮箱
    
    @Column(length = 20)
    private String provider; // CLAUDE, OPENAI, GEMINI
    
    @Column(length = 100)
    private String model;
    
    @Column(length = 10)
    private String method; // GET, POST, PUT, DELETE
    
    @Column(length = 200)
    private String endpoint; // /v1/messages, /v1/completions等
    
    @Column(length = 20)
    private String status; // success, failed, pending
    
    private Integer requestTokens;
    private Integer responseTokens;
    private Integer totalTokens;
    
    private Integer latencyMs; // 响应时间（毫秒）
    
    private Integer statusCode;
    
    @Column(length = 50)
    private String errorType; // authentication_error, rate_limit_error, api_error等
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(columnDefinition = "TEXT")
    private String requestPath;
    
    private Long requestSize; // 请求大小（字节）
    private Long responseSize; // 响应大小（字节）
    
    @Column(length = 45)
    private String clientIp;
    
    @Column(length = 200)
    private String userAgent;
    
    @Column(length = 50)
    private String requestId; // 请求追踪ID
    
    @Column
    private Integer retryCount; // 重试次数
    
    @Column(columnDefinition = "TEXT")
    private String failedAccounts; // 失败的账号列表，JSON格式
    
    @Column(length = 100)
    private String finalAccount; // 最终使用的账号
    
    @Column(columnDefinition = "TEXT")
    private String requestBody; // 请求体内容（用于错误排查）
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apiKeyId", insertable = false, updatable = false)
    private ApiKeyEntity apiKey;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accountId", insertable = false, updatable = false)
    private AccountEntity account;
}