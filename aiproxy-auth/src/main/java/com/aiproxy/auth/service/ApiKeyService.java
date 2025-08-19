package com.aiproxy.auth.service;

import com.aiproxy.common.entity.ApiKeyEntity;
import com.aiproxy.common.model.ApiKey;
import com.aiproxy.common.model.RateLimitRule;
import com.aiproxy.common.repository.ApiKeyRepository;
import com.aiproxy.common.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ApiKeyService {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ApiKeyRepository apiKeyRepository;
    private static final String KEY_PREFIX = "apikey:";
    
    public ApiKeyService(ReactiveRedisTemplate<String, String> redisTemplate,
                        ApiKeyRepository apiKeyRepository) {
        this.redisTemplate = redisTemplate;
        this.apiKeyRepository = apiKeyRepository;
    }
    
    public Mono<ApiKey> validateApiKey(String key) {
        return getApiKey(key)
            .filter(ApiKey::isValid)
            .doOnNext(apiKey -> updateLastUsed(apiKey));
    }
    
    public Mono<ApiKey> getApiKey(String key) {
        String redisKey = KEY_PREFIX + key;
        String keyHash = hashApiKey(key);
        
        // Try cache first
        return redisTemplate.opsForValue()
            .get(redisKey)
            .map(json -> JsonUtil.fromJson(json, ApiKey.class))
            .switchIfEmpty(
                // If not in cache, get from database
                Mono.fromCallable(() -> apiKeyRepository.findByKeyHash(keyHash).orElse(null))
                    .subscribeOn(Schedulers.boundedElastic())
                    .map(this::convertToModel)
                    .flatMap(apiKey -> {
                        if (apiKey != null) {
                            // Update cache
                            String json = JsonUtil.toJson(apiKey);
                            return redisTemplate.opsForValue()
                                .set(redisKey, json, Duration.ofHours(1))
                                .thenReturn(apiKey);
                        }
                        // Return empty if API key not found
                        return Mono.empty();
                    })
            );
    }
    
    public Mono<ApiKey> createApiKey(String name, String description) {
        // 生成新的API Key
        String generatedKey = generateApiKey();
        
        ApiKey apiKey = ApiKey.builder()
            .id(UUID.randomUUID().toString())
            .key(generatedKey)  // 保留完整的key用于返回给用户
            .name(name)
            .description(description)
            .enabled(true)
            .createdAt(LocalDateTime.now())
            .rateLimitRule(RateLimitRule.defaultRule())
            .totalRequests(0L)
            .totalTokens(0L)
            .build();
        
        // 保存时会处理key的存储
        return saveApiKeyWithOriginalKey(apiKey);
    }
    
    public Mono<ApiKey> saveApiKey(ApiKey apiKey) {
        if (apiKey.getId() == null) {
            apiKey.setId(UUID.randomUUID().toString());
        }
        
        // Save to database first
        return Mono.fromCallable(() -> {
            ApiKeyEntity entity = convertToEntity(apiKey);
            return apiKeyRepository.save(entity);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .map(this::convertToModel)
        .flatMap(savedKey -> {
            // Then update Redis cache
            String redisKey = KEY_PREFIX + savedKey.getKey();
            String json = JsonUtil.toJson(savedKey);
            
            return redisTemplate.opsForValue()
                .set(redisKey, json, Duration.ofHours(1))
                .thenReturn(savedKey);
        });
    }
    
    public Mono<Boolean> deleteApiKey(String key) {
        String redisKey = KEY_PREFIX + key;
        String keyHash = hashApiKey(key);
        
        // Delete from database first
        return Mono.fromCallable(() -> {
            var entity = apiKeyRepository.findByKeyHash(keyHash);
            if (entity.isPresent()) {
                apiKeyRepository.delete(entity.get());
                return true;
            }
            return false;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(deleted -> {
            if (deleted) {
                // Then remove from cache
                return redisTemplate.delete(redisKey)
                    .thenReturn(true);
            }
            return Mono.just(false);
        });
    }
    
    public Mono<Boolean> deleteApiKeyById(String id) {
        // Delete from database first by ID
        return Mono.fromCallable(() -> {
            var entity = apiKeyRepository.findById(id);
            if (entity.isPresent()) {
                // Get the actual key for cache deletion
                ApiKeyEntity apiKeyEntity = entity.get();
                apiKeyRepository.delete(apiKeyEntity);
                return apiKeyEntity;
            }
            return null;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(entity -> {
            if (entity != null) {
                // Clear all possible cache entries (we don't have the actual key, so clear by pattern)
                // Note: This is a workaround since we store masked keys
                return redisTemplate.keys(KEY_PREFIX + "*")
                    .flatMap(keys -> redisTemplate.delete(keys))
                    .then(Mono.just(true));
            }
            return Mono.just(false);
        });
    }
    
    private void updateLastUsed(ApiKey apiKey) {
        apiKey.setLastUsedAt(LocalDateTime.now());
        apiKey.setTotalRequests(apiKey.getTotalRequests() + 1);
        
        // Update database asynchronously
        Mono.fromRunnable(() -> {
            apiKeyRepository.updateUsageStatistics(apiKey.getId(), 0L);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .subscribe();
        
        // Update cache
        String redisKey = KEY_PREFIX + apiKey.getKey();
        String json = JsonUtil.toJson(apiKey);
        redisTemplate.opsForValue()
            .set(redisKey, json, Duration.ofHours(1))
            .subscribe();
    }
    
    // 新增方法：保存API key时保留原始key（仅用于创建时）
    private Mono<ApiKey> saveApiKeyWithOriginalKey(ApiKey apiKey) {
        if (apiKey.getId() == null) {
            apiKey.setId(UUID.randomUUID().toString());
        }
        
        String originalKey = apiKey.getKey(); // 保存原始key
        
        // Save to database first
        return Mono.fromCallable(() -> {
            ApiKeyEntity entity = convertToEntityWithOriginalKey(apiKey);
            return apiKeyRepository.save(entity);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .map(savedEntity -> {
            // 返回时使用原始key
            ApiKey savedKey = convertToModel(savedEntity);
            savedKey.setKey(originalKey); // 恢复原始key用于返回
            return savedKey;
        })
        .flatMap(savedKey -> {
            // Then update Redis cache with original key
            String redisKey = KEY_PREFIX + originalKey;
            String json = JsonUtil.toJson(savedKey);
            
            return redisTemplate.opsForValue()
                .set(redisKey, json, Duration.ofHours(1))
                .thenReturn(savedKey);
        });
    }
    
    public Flux<ApiKey> getAllApiKeys() {
        // Get all API keys from database - use defer to ensure fresh data
        return Flux.defer(() -> 
            Flux.fromIterable(apiKeyRepository.findAll())
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::convertToModel)
        );
    }
    
    
    private String generateApiKey() {
        // 生成16位随机字符串 (足够安全且不会太长)
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return "sk-aigate-" + randomPart;
    }
    
    private ApiKey createDefaultApiKey(String key) {
        return ApiKey.builder()
            .id(UUID.randomUUID().toString())
            .key(key)
            .name("Default API Key")
            .description("Auto-generated default API key")
            .enabled(true)
            .createdAt(LocalDateTime.now())
            .rateLimitRule(RateLimitRule.defaultRule())
            .totalRequests(0L)
            .totalTokens(0L)
            .build();
    }
    
    private String hashApiKey(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
    
    private ApiKeyEntity convertToEntity(ApiKey model) {
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setId(model.getId());
        entity.setKeyHash(hashApiKey(model.getKey()));
        entity.setName(model.getName());
        entity.setCreatedBy("system"); // ApiKey doesn't have createdBy field
        entity.setIsActive(model.isEnabled());
        entity.setRateLimit(model.getRateLimitRule() != null ? 
            model.getRateLimitRule().getRequestsPerMinute() : 1000);
        entity.setDailyTokenLimit(model.getRateLimitRule() != null ? 
            model.getRateLimitRule().getTokensPerDay() : 1000000L);
        entity.setCreatedAt(model.getCreatedAt());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setLastUsedAt(model.getLastUsedAt());
        entity.setTotalRequests(model.getTotalRequests());
        entity.setTotalTokens(model.getTotalTokens());
        entity.setAllowedModels(null); // ApiKey doesn't have allowedModels field
        entity.setMetadata(null); // ApiKey doesn't have metadata field
        return entity;
    }
    
    // 新增方法：创建API key时使用
    private ApiKeyEntity convertToEntityWithOriginalKey(ApiKey model) {
        ApiKeyEntity entity = convertToEntity(model);
        // 直接返回entity，不再处理keyPrefix
        return entity;
    }
    
    private ApiKey convertToModel(ApiKeyEntity entity) {
        if (entity == null) return null;
        
        RateLimitRule rateLimitRule = RateLimitRule.builder()
            .requestsPerMinute(entity.getRateLimit())
            .tokensPerDay(entity.getDailyTokenLimit())
            .build();
        
        // 生成一个显示用的掩码key
        String displayKey = "sk-aigate-" + entity.getId().replace("-", "").substring(0, 8) + "...";
        
        return ApiKey.builder()
            .id(entity.getId())
            .key(displayKey) // 显示掩码版本的Key
            .name(entity.getName())
            .enabled(entity.getIsActive())
            .createdAt(entity.getCreatedAt())
            .lastUsedAt(entity.getLastUsedAt())
            .rateLimitRule(rateLimitRule)
            .totalRequests(entity.getTotalRequests())
            .totalTokens(entity.getTotalTokens())
            .build();
    }
}