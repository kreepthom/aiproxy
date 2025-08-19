package com.aiproxy.core.service;

import com.aiproxy.common.enums.AccountStatus;
import com.aiproxy.common.exception.RelayException;
import com.aiproxy.common.model.Account;
import com.aiproxy.common.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class AccountPoolService {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final Map<String, AccountHealth> healthMap = new ConcurrentHashMap<>();
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);
    private static final String ACCOUNT_PREFIX = "account:";
    
    public AccountPoolService(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        // Initialize with a demo account
        // initializeDemoAccount(); // Disabled - use real accounts from database
    }
    
    public Mono<Account> selectAccount() {
        return selectAccountWithExclusions(new java.util.HashSet<>());
    }
    
    public Mono<Account> selectAccountWithExclusions(java.util.Set<String> excludedIds) {
        return getAvailableAccounts()
            .filter(account -> !excludedIds.contains(account.getId()))
            .collectList()
            .flatMap(accounts -> {
                if (accounts.isEmpty()) {
                    return Mono.error(new RelayException("No available accounts"));
                }
                
                // Random selection for better distribution
                int randomIndex = new java.util.Random().nextInt(accounts.size());
                Account selected = accounts.get(randomIndex);
                
                log.debug("Selected account: {} from {} available (excluded: {})", 
                    selected.getId(), accounts.size(), excludedIds.size());
                
                return Mono.just(selected);
            });
    }
    
    private Flux<Account> getAvailableAccounts() {
        return redisTemplate.keys(ACCOUNT_PREFIX + "*")
            .flatMap(key -> redisTemplate.opsForValue().get(key))
            .map(json -> JsonUtil.fromJson(json, Account.class))
            .filter(this::isAccountAvailable)
            .sort(Comparator.comparing(Account::getLastUsedAt, 
                Comparator.nullsFirst(Comparator.naturalOrder())));
    }
    
    private boolean isAccountAvailable(Account account) {
        // Check account status
        if (!account.isActive()) {
            return false;
        }
        
        // Check health status
        AccountHealth health = healthMap.get(account.getId());
        if (health != null && !health.isHealthy()) {
            return false;
        }
        
        return true;
    }
    
    public void markAccountSuccess(String accountId) {
        AccountHealth health = healthMap.computeIfAbsent(accountId, k -> new AccountHealth());
        health.recordSuccess();
        updateAccountLastUsed(accountId);
    }
    
    public void markAccountFailed(String accountId, Throwable error) {
        AccountHealth health = healthMap.computeIfAbsent(accountId, k -> new AccountHealth());
        health.recordFailure(error);
        
        if (health.getConsecutiveFailures() > 5) {
            disableAccount(accountId);
            log.warn("Account {} disabled after 5 consecutive failures", accountId);
        }
    }
    
    private void updateAccountLastUsed(String accountId) {
        String key = ACCOUNT_PREFIX + accountId;
        redisTemplate.opsForValue().get(key)
            .map(json -> JsonUtil.fromJson(json, Account.class))
            .doOnNext(account -> {
                account.setLastUsedAt(LocalDateTime.now());
                // 修复空指针异常：如果usageCount为null，初始化为0
                Long currentUsage = account.getUsageCount();
                account.setUsageCount((currentUsage != null ? currentUsage : 0L) + 1);
                String updatedJson = JsonUtil.toJson(account);
                // 保持7天的TTL
                redisTemplate.opsForValue().set(key, updatedJson, Duration.ofDays(7)).subscribe();
            })
            .subscribe();
    }
    
    private void disableAccount(String accountId) {
        String key = ACCOUNT_PREFIX + accountId;
        redisTemplate.opsForValue().get(key)
            .map(json -> JsonUtil.fromJson(json, Account.class))
            .doOnNext(account -> {
                account.setStatus(AccountStatus.ERROR);
                String updatedJson = JsonUtil.toJson(account);
                // 保持7天的TTL
                redisTemplate.opsForValue().set(key, updatedJson, Duration.ofDays(7)).subscribe();
            })
            .subscribe();
    }
    
    @Scheduled(fixedDelay = 30000) // Check every 30 seconds
    public void performHealthCheck() {
        log.debug("Performing health check on accounts");
        // 获取所有账户（包括ERROR状态的）
        redisTemplate.keys(ACCOUNT_PREFIX + "*")
            .flatMap(key -> redisTemplate.opsForValue().get(key))
            .map(json -> JsonUtil.fromJson(json, Account.class))
            .parallel()
            .runOn(Schedulers.parallel())
            .doOnNext(account -> {
                AccountHealth health = healthMap.computeIfAbsent(account.getId(), 
                    k -> new AccountHealth());
                
                // 如果账户是ERROR状态，且已经过了5分钟，尝试恢复
                if (account.getStatus() == AccountStatus.ERROR && 
                    health.getLastCheckTime() != null &&
                    health.getLastCheckTime().plusMinutes(5).isBefore(LocalDateTime.now())) {
                    log.info("Attempting to recover account {} from ERROR status", account.getId());
                    recoverAccount(account.getId());
                    health.reset();
                }
                
                // Reset health if it's been stable for a while
                if (health.getConsecutiveFailures() == 0 && 
                    health.getLastCheckTime() != null &&
                    health.getLastCheckTime().plusMinutes(5).isBefore(LocalDateTime.now())) {
                    health.reset();
                }
            })
            .subscribe();
    }
    
    private void recoverAccount(String accountId) {
        String key = ACCOUNT_PREFIX + accountId;
        redisTemplate.opsForValue().get(key)
            .map(json -> JsonUtil.fromJson(json, Account.class))
            .doOnNext(account -> {
                account.setStatus(AccountStatus.ACTIVE);
                String updatedJson = JsonUtil.toJson(account);
                redisTemplate.opsForValue().set(key, updatedJson, Duration.ofDays(7)).subscribe();
                log.info("Account {} recovered to ACTIVE status", accountId);
            })
            .subscribe();
    }
    
    private void initializeDemoAccount() {
        // Create a demo account for testing
        Account demoAccount = Account.builder()
            .id(UUID.randomUUID().toString())
            .name("Demo Account")
            .email("demo@claude-relay.com")
            .accessToken("demo-token-" + UUID.randomUUID())
            .refreshToken("demo-refresh-" + UUID.randomUUID())
            .status(AccountStatus.ACTIVE)
            .expireAt(LocalDateTime.now().plusDays(30))
            .createdAt(LocalDateTime.now())
            .usageCount(0L)
            .build();
        
        String key = ACCOUNT_PREFIX + demoAccount.getId();
        String json = JsonUtil.toJson(demoAccount);
        redisTemplate.opsForValue().set(key, json, Duration.ofDays(30))
            .subscribe(success -> {
                if (Boolean.TRUE.equals(success)) {
                    log.info("Demo account created: {}", demoAccount.getId());
                }
            });
    }
    
    // Inner class for tracking account health
    private static class AccountHealth {
        private int consecutiveFailures = 0;
        private int consecutiveSuccesses = 0;
        private LocalDateTime lastCheckTime;
        private Throwable lastError;
        
        public void recordSuccess() {
            consecutiveSuccesses++;
            consecutiveFailures = 0;
            lastCheckTime = LocalDateTime.now();
        }
        
        public void recordFailure(Throwable error) {
            consecutiveFailures++;
            consecutiveSuccesses = 0;
            lastError = error;
            lastCheckTime = LocalDateTime.now();
        }
        
        public boolean isHealthy() {
            return consecutiveFailures < 3;
        }
        
        public void reset() {
            consecutiveFailures = 0;
            consecutiveSuccesses = 0;
            lastError = null;
        }
        
        public int getConsecutiveFailures() {
            return consecutiveFailures;
        }
        
        public LocalDateTime getLastCheckTime() {
            return lastCheckTime;
        }
    }
}