package com.aiproxy.core.service;

import com.aiproxy.auth.service.AccountService;
import com.aiproxy.common.enums.AccountStatus;
import com.aiproxy.common.exception.RelayException;
import com.aiproxy.common.model.Account;
import com.aiproxy.common.model.ClaudeAccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class AccountPoolService {
    
    private final AccountService accountService;
    private final Map<String, AccountHealth> healthMap = new ConcurrentHashMap<>();
    
    @Autowired
    public AccountPoolService(AccountService accountService) {
        this.accountService = accountService;
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
        // 直接从数据库获取账号，并检查token是否需要刷新
        return accountService.getAllActiveAccounts()
            .flatMap(claudeAccount -> {
                // 检查token是否需要刷新（提前30分钟）
                if (claudeAccount.getTokenExpiresAt() != null) {
                    LocalDateTime refreshThreshold = LocalDateTime.now().plusMinutes(30);
                    if (claudeAccount.getTokenExpiresAt().isBefore(refreshThreshold)) {
                        log.info("Token for account {} will expire soon, refreshing...", claudeAccount.getEmail());
                        return accountService.refreshAccountToken(claudeAccount)
                            .map(this::convertToAccount)
                            .onErrorResume(error -> {
                                log.error("Failed to refresh token for account {}: {}", 
                                    claudeAccount.getEmail(), error.getMessage());
                                // 如果刷新失败，标记账号为过期
                                claudeAccount.setStatus("EXPIRED");
                                return Mono.just(convertToAccount(claudeAccount));
                            });
                    }
                }
                return Mono.just(convertToAccount(claudeAccount));
            })
            .filter(this::isAccountAvailable)
            .sort(Comparator.comparing(Account::getLastUsedAt, 
                Comparator.nullsFirst(Comparator.naturalOrder())));
    }
    
    private Account convertToAccount(ClaudeAccount claudeAccount) {
        // 转换ClaudeAccount为Account
        AccountStatus accountStatus = claudeAccount.isEnabled() 
            ? AccountStatus.ACTIVE 
            : AccountStatus.DISABLED;
            
        // 如果状态是EXPIRED，使用EXPIRED状态
        if ("EXPIRED".equals(claudeAccount.getStatus())) {
            accountStatus = AccountStatus.EXPIRED;
        }
        
        return Account.builder()
            .id(claudeAccount.getId())
            .name(claudeAccount.getEmail())
            .email(claudeAccount.getEmail())
            .accessToken(claudeAccount.getAccessToken())
            .refreshToken(claudeAccount.getRefreshToken())
            .status(accountStatus)
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
        
        // 更新数据库中的最后使用时间
        accountService.getAccountById(accountId)
            .flatMap(account -> {
                account.setLastUsedAt(LocalDateTime.now());
                Long requests = account.getTotalRequests();
                account.setTotalRequests((requests != null ? requests : 0L) + 1);
                return accountService.saveAccount(account);
            })
            .subscribe(
                success -> log.debug("Updated last used time for account {}", accountId),
                error -> log.error("Failed to update account {}: {}", accountId, error.getMessage())
            );
    }
    
    public void markAccountFailed(String accountId, Throwable error) {
        AccountHealth health = healthMap.computeIfAbsent(accountId, k -> new AccountHealth());
        health.recordFailure(error);
        
        if (health.getConsecutiveFailures() > 5) {
            // 禁用账号
            accountService.getAccountById(accountId)
                .flatMap(account -> {
                    account.setStatus("ERROR");
                    account.setEnabled(false);
                    return accountService.saveAccount(account);
                })
                .subscribe(
                    success -> log.warn("Account {} disabled after 5 consecutive failures", accountId),
                    err -> log.error("Failed to disable account {}: {}", accountId, err.getMessage())
                );
        }
    }
    
    @Scheduled(fixedDelay = 300000) // Check every 5 minutes
    public void performHealthCheck() {
        log.debug("Performing health check on accounts");
        
        // 重置长时间未使用的健康状态
        healthMap.entrySet().removeIf(entry -> {
            AccountHealth health = entry.getValue();
            if (health.getLastCheckTime() != null &&
                health.getLastCheckTime().plusMinutes(10).isBefore(LocalDateTime.now())) {
                log.debug("Removing stale health status for account {}", entry.getKey());
                return true;
            }
            return false;
        });
        
        // 尝试恢复ERROR状态的账号
        accountService.getAllAccounts()
            .filter(account -> "ERROR".equals(account.getStatus()))
            .flatMap(account -> {
                AccountHealth health = healthMap.get(account.getId());
                if (health == null || 
                    (health.getLastCheckTime() != null && 
                     health.getLastCheckTime().plusMinutes(5).isBefore(LocalDateTime.now()))) {
                    log.info("Attempting to recover account {} from ERROR status", account.getId());
                    account.setStatus("ACTIVE");
                    account.setEnabled(true);
                    healthMap.remove(account.getId());
                    return accountService.saveAccount(account);
                }
                return Mono.empty();
            })
            .subscribe(
                account -> log.info("Account {} recovered to ACTIVE status", account.getEmail()),
                error -> log.error("Error during health check: {}", error.getMessage())
            );
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