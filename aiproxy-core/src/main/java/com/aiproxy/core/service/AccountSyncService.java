package com.aiproxy.core.service;

import com.aiproxy.auth.service.AccountService;
import com.aiproxy.common.enums.AccountStatus;
import com.aiproxy.common.model.Account;
import com.aiproxy.common.model.ClaudeAccount;
import com.aiproxy.common.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Service to sync OAuth accounts to the account pool
 */
@Service
@Slf4j
public class AccountSyncService {
    
    private final AccountService accountService;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private static final String ACCOUNT_PREFIX = "account:";
    
    @Autowired
    public AccountSyncService(AccountService accountService, 
                             ReactiveRedisTemplate<String, String> redisTemplate) {
        this.accountService = accountService;
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * Sync OAuth accounts to account pool every 30 seconds
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 5000)
    public void syncAccounts() {
        log.info("Starting OAuth accounts sync to account pool");
        
        accountService.getAllAccounts()
            .doOnNext(account -> log.info("Found account: {}, enabled={}, status={}", 
                account.getEmail(), account.isEnabled(), account.getStatus()))
            // 同步所有账号到账号池，保持状态
            .flatMap(this::convertAndSaveAccount)
            .doOnComplete(() -> log.info("OAuth accounts sync completed"))
            .doOnError(error -> log.error("Failed to sync OAuth accounts: ", error))
            .subscribe();
    }
    
    private Mono<Boolean> convertAndSaveAccount(ClaudeAccount claudeAccount) {
        // Convert ClaudeAccount to Account for the pool
        // 如果账号是启用的，强制状态为ACTIVE
        AccountStatus accountStatus = claudeAccount.isEnabled() 
            ? AccountStatus.ACTIVE 
            : AccountStatus.DISABLED;
            
        Account account = Account.builder()
            .id(claudeAccount.getId())
            .name(claudeAccount.getEmail())
            .email(claudeAccount.getEmail())
            .accessToken(claudeAccount.getAccessToken())
            .refreshToken(claudeAccount.getRefreshToken())
            .status(accountStatus)  // 使用基于enabled字段的状态
            .expireAt(claudeAccount.getTokenExpiresAt() != null 
                ? claudeAccount.getTokenExpiresAt() 
                : LocalDateTime.now().plusDays(7))
            .createdAt(claudeAccount.getCreatedAt() != null 
                ? claudeAccount.getCreatedAt() 
                : LocalDateTime.now())
            .lastUsedAt(claudeAccount.getLastUsedAt())
            .usageCount(claudeAccount.getTotalRequests() != null 
                ? claudeAccount.getTotalRequests().longValue() 
                : 0L)
            .build();
        
        String key = ACCOUNT_PREFIX + account.getId();
        String json = JsonUtil.toJson(account);
        
        // Save to Redis with TTL of 7 days (will be refreshed on next sync)
        return redisTemplate.opsForValue()
            .set(key, json, Duration.ofDays(7))
            .doOnSuccess(success -> {
                if (Boolean.TRUE.equals(success)) {
                    log.debug("Synced OAuth account {} to pool", claudeAccount.getEmail());
                }
            });
    }
    
    private AccountStatus mapStatus(String status) {
        if (status == null) {
            return AccountStatus.ACTIVE;
        }
        
        switch (status.toUpperCase()) {
            case "ACTIVE":
                return AccountStatus.ACTIVE;
            case "EXPIRED":
                return AccountStatus.EXPIRED;
            case "DISABLED":
                return AccountStatus.DISABLED;  // 使用新的 DISABLED 枚举值
            case "INACTIVE":
                return AccountStatus.INACTIVE;
            case "RATE_LIMITED":
                return AccountStatus.ERROR;
            default:
                return AccountStatus.ACTIVE;
        }
    }
    
    /**
     * Force sync all accounts immediately
     */
    public Mono<Long> forceSyncNow() {
        log.info("Force syncing all OAuth accounts to pool");
        
        return accountService.getAllAccounts()
            // 只同步启用的账号，不修改状态
            .filter(claudeAccount -> claudeAccount.isEnabled())
            .flatMap(this::convertAndSaveAccount)
            .count()
            .doOnSuccess(count -> log.info("Force synced {} accounts to pool", count));
    }
}