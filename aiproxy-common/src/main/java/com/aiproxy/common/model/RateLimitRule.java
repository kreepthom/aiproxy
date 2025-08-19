package com.aiproxy.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitRule implements Serializable {
    
    private int requestsPerMinute;
    private int requestsPerHour;
    private int requestsPerDay;
    private long tokensPerDay;
    private int concurrentRequests;
    
    public static RateLimitRule defaultRule() {
        return RateLimitRule.builder()
                .requestsPerMinute(60)
                .requestsPerHour(1000)
                .requestsPerDay(10000)
                .tokensPerDay(1000000)
                .concurrentRequests(10)
                .build();
    }
}