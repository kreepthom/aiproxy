package com.aiproxy.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Account pool configuration
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "relay.account-pool")
public class AccountPoolConfig {
    
    /**
     * Maximum retry attempts when an account fails
     * 0 = no retry, only try once
     * Default: 3
     */
    private int maxRetryAttempts = 3;
    
    /**
     * Whether to enable retry mechanism
     * Default: true
     */
    private boolean enableRetry = true;
    
    /**
     * Account selection strategy
     * Options: random, round-robin, least-used
     * Default: random
     */
    private String selectionStrategy = "random";
    
    /**
     * Health check interval in milliseconds
     * Default: 30000 (30 seconds)
     */
    private long healthCheckInterval = 30000;
    
    /**
     * Account sync interval in milliseconds
     * Default: 30000 (30 seconds)
     */
    private long accountSyncInterval = 30000;
    
    /**
     * Maximum consecutive failures before disabling an account
     * Default: 5
     */
    private int maxConsecutiveFailures = 5;
    
    /**
     * Health threshold - consecutive failures before marking as unhealthy
     * Default: 3
     */
    private int healthThreshold = 3;
    
    /**
     * Get effective max retries based on configuration
     */
    public int getEffectiveMaxRetries() {
        if (!enableRetry) {
            return 0;
        }
        return Math.max(0, maxRetryAttempts);
    }
}