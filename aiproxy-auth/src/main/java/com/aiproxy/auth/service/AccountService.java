package com.aiproxy.auth.service;

import com.aiproxy.common.entity.AccountEntity;
import com.aiproxy.common.model.ClaudeAccount;
import com.aiproxy.common.repository.AccountRepository;
import com.aiproxy.common.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class AccountService {
    
    private static final String ACCOUNT_KEY_PREFIX = "oauth:account:";
    private static final String ACCOUNT_SET_KEY = "oauth:accounts:all";
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ClaudeOAuthService oauthService;
    private final AccountRepository accountRepository;
    
    public AccountService(ReactiveRedisTemplate<String, String> redisTemplate,
                         ClaudeOAuthService oauthService,
                         AccountRepository accountRepository) {
        this.redisTemplate = redisTemplate;
        this.oauthService = oauthService;
        this.accountRepository = accountRepository;
    }
    
    public Mono<ClaudeAccount> createAccountFromAuthCode(String code, String codeVerifier, String email) {
        return oauthService.exchangeCodeForToken(code, codeVerifier)
            .flatMap(tokens -> {
                String accessToken = (String) tokens.get("access_token");
                String refreshToken = (String) tokens.get("refresh_token");
                Integer expiresIn = (Integer) tokens.get("expires_in");
                
                ClaudeAccount account = ClaudeAccount.builder()
                    .id(UUID.randomUUID().toString())
                    .email(email)
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn))
                    .createdAt(LocalDateTime.now())
                    .enabled(true)
                    .status("ACTIVE")
                    .totalRequests(0L)
                    .totalTokens(0L)
                    .build();
                
                return saveAccount(account);
            });
    }
    
    public Mono<ClaudeAccount> saveAccount(ClaudeAccount account) {
        if (account.getId() == null) {
            account.setId(UUID.randomUUID().toString());
        }
        if (account.getCreatedAt() == null) {
            account.setCreatedAt(LocalDateTime.now());
        }
        
        // Save to database only (no Redis cache)
        return Mono.fromCallable(() -> {
            AccountEntity entity = convertToEntity(account);
            return accountRepository.save(entity);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .map(this::convertToModel)
        .doOnSuccess(acc -> log.info("Saved Claude account: {}", acc.getEmail()));
    }
    
    public Mono<ClaudeAccount> getAccount(String accountId) {
        // Get directly from database (no cache)
        return Mono.fromCallable(() -> accountRepository.findById(accountId).orElse(null))
            .subscribeOn(Schedulers.boundedElastic())
            .map(this::convertToModel);
    }
    
    public Flux<ClaudeAccount> getAllActiveAccounts() {
        // Get from database instead of just cache
        return Flux.fromIterable(
            accountRepository.findByEnabledTrueAndStatus("ACTIVE")
        )
        .subscribeOn(Schedulers.boundedElastic())
        .map(this::convertToModel);
    }
    
    public Mono<ClaudeAccount> refreshAccountToken(ClaudeAccount account) {
        if (!account.isTokenExpired()) {
            return Mono.just(account);
        }
        
        return oauthService.refreshAccessToken(account.getRefreshToken())
            .flatMap(tokens -> {
                String newAccessToken = (String) tokens.get("access_token");
                Integer expiresIn = (Integer) tokens.get("expires_in");
                
                account.setAccessToken(newAccessToken);
                account.setTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
                
                return saveAccount(account);
            })
            .doOnSuccess(acc -> log.info("Refreshed token for account: {}", acc.getEmail()))
            .onErrorResume(error -> {
                log.error("Failed to refresh token for account {}: ", account.getEmail(), error);
                account.setStatus("EXPIRED");
                return saveAccount(account);
            });
    }
    
    public Mono<Boolean> deleteAccount(String accountId) {
        // Delete from database only (no cache to clean)
        return Mono.fromCallable(() -> {
            if (accountRepository.existsById(accountId)) {
                accountRepository.deleteById(accountId);
                return true;
            }
            return false;
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    public Flux<ClaudeAccount> getAllAccounts() {
        // Get all from database - use defer to ensure fresh data
        return Flux.defer(() -> 
            Flux.fromIterable(accountRepository.findAll())
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::convertToModel)
        );
    }
    
    public Mono<ClaudeAccount> getAccountById(String id) {
        return getAccount(id);
    }
    
    public Mono<ClaudeAccount> updateAccountStatus(String id, String status, boolean enabled) {
        return getAccount(id)
            .flatMap(account -> {
                account.setEnabled(enabled);
                
                // 确保状态和启用标志一致
                if (enabled) {
                    // 如果启用账号，状态应该是ACTIVE
                    account.setStatus("ACTIVE");
                } else {
                    // 如果禁用账号，状态应该是DISABLED
                    account.setStatus("DISABLED");
                }
                
                log.info("Updating account {} status: enabled={}, status={}", 
                    account.getEmail(), account.isEnabled(), account.getStatus());
                
                return saveAccount(account);
            });
    }
    
    private AccountEntity convertToEntity(ClaudeAccount model) {
        AccountEntity entity = new AccountEntity();
        entity.setId(model.getId());
        entity.setEmail(model.getEmail());
        entity.setProvider("CLAUDE");
        entity.setAccessToken(model.getAccessToken());
        entity.setRefreshToken(model.getRefreshToken());
        entity.setTokenExpiresAt(model.getTokenExpiresAt());
        entity.setEnabled(model.isEnabled());
        entity.setStatus(model.getStatus());
        entity.setCreatedAt(model.getCreatedAt());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setLastUsedAt(model.getLastUsedAt());
        entity.setTotalRequests(model.getTotalRequests());
        entity.setTotalTokens(model.getTotalTokens());
        entity.setMetadata(null); // ClaudeAccount doesn't have metadata field
        return entity;
    }
    
    private ClaudeAccount convertToModel(AccountEntity entity) {
        if (entity == null) return null;
        
        return ClaudeAccount.builder()
            .id(entity.getId())
            .email(entity.getEmail())
            .accessToken(entity.getAccessToken())
            .refreshToken(entity.getRefreshToken())
            .tokenExpiresAt(entity.getTokenExpiresAt())
            .enabled(entity.getEnabled())
            .status(entity.getStatus())
            .createdAt(entity.getCreatedAt())
            .lastUsedAt(entity.getLastUsedAt())
            .totalRequests(entity.getTotalRequests())
            .totalTokens(entity.getTotalTokens())
            .build();
    }
}