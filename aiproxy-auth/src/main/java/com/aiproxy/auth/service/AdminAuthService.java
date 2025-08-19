package com.aiproxy.auth.service;

import com.aiproxy.common.entity.AdminUserEntity;
import com.aiproxy.common.repository.AdminUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class AdminAuthService {
    
    private static final String TOKEN_PREFIX = "admin:token:";
    private static final String ADMIN_USER_KEY = "admin:user";
    private static final Duration TOKEN_EXPIRY = Duration.ofDays(1);
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AdminUserRepository adminUserRepository;
    
    @Value("${relay.admin.username:admin}")
    private String adminUsername;
    
    @Value("${relay.admin.password:}")
    private String adminPassword;
    
    public AdminAuthService(ReactiveRedisTemplate<String, String> redisTemplate,
                           PasswordEncoder passwordEncoder,
                           AdminUserRepository adminUserRepository) {
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = passwordEncoder;
        this.adminUserRepository = adminUserRepository;
    }
    
    public Mono<String> login(String username, String password) {
        // 从数据库验证用户
        return Mono.fromCallable(() -> adminUserRepository.findByUsernameAndIsActiveTrue(username))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(optionalUser -> {
                if (optionalUser.isEmpty()) {
                    return Mono.empty();
                }
                
                AdminUserEntity user = optionalUser.get();
                
                // 验证密码
                if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                    return Mono.empty();
                }
                
                // 更新最后登录时间
                user.setLastLoginAt(LocalDateTime.now());
                return Mono.fromCallable(() -> adminUserRepository.save(user))
                    .subscribeOn(Schedulers.boundedElastic())
                    .then(Mono.defer(() -> {
                        // 生成 token
                        String token = UUID.randomUUID().toString();
                        // 存储 token 到 Redis（用于快速验证）
                        return redisTemplate.opsForValue()
                            .set(TOKEN_PREFIX + token, username, TOKEN_EXPIRY)
                            .thenReturn(token);
                    }));
            });
    }
    
    public Mono<Boolean> logout(String token) {
        return redisTemplate.delete(TOKEN_PREFIX + token)
            .map(count -> count > 0);
    }
    
    public Mono<Boolean> validateToken(String token) {
        return redisTemplate.hasKey(TOKEN_PREFIX + token)
            .defaultIfEmpty(false)  // 确保永远不返回空的 Mono
            .doOnNext(exists -> {
                String maskedToken = token != null && token.length() >= 8 
                    ? token.substring(0, 8) + "..." 
                    : token + "...";
                log.debug("Token validation for {}: {}", maskedToken, exists);
            });
    }
    
    // 创建管理员用户（由AdminInitializer调用）
    public Mono<Void> createAdminUser(String username, String password) {
        return Mono.fromCallable(() -> {
            // 检查用户是否已存在
            if (adminUserRepository.existsByUsername(username)) {
                log.info("管理员用户 {} 已存在", username);
                return false;
            }
            
            // 创建新的管理员用户
            AdminUserEntity adminUser = AdminUserEntity.builder()
                .id(UUID.randomUUID().toString())
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .isActive(true)
                .build();
            
            adminUserRepository.save(adminUser);
            log.info("管理员用户 {} 已创建并保存到数据库", username);
            return true;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .doOnNext(created -> {
            if (created) {
                displayAdminCredentials(username, password);
            }
        })
        .then();
    }
    
    private String generateDefaultPassword() {
        // 生成一个随机密码
        return UUID.randomUUID().toString().substring(0, 12);
    }
    
    private void displayAdminCredentials(String username, String password) {
        String banner = """
            
            ================================================================================
            重要提示：管理员账号已生成
            ================================================================================
            
            首次启动自动生成的管理员账号信息：
            
            用户名: %s
            密码: %s
            
            请立即保存这些信息！密码已加密存储在 Redis 中。
            
            登录方式:
            POST /auth/login
            {
              "username": "%s",
              "password": "%s"
            }
            
            ================================================================================
            
            """.formatted(username, password, username, password);
        
        log.info(banner);
        
        // 同时写入临时文件
        try {
            String homeDir = System.getProperty("user.home");
            java.nio.file.Path tempFile = java.nio.file.Paths.get(homeDir, ".claude-relay-admin-credentials.txt");
            String content = String.format("用户名: %s\n密码: %s\n", username, password);
            java.nio.file.Files.writeString(tempFile, content);
            log.info("管理员账号信息已保存到: {}", tempFile.toAbsolutePath());
        } catch (Exception e) {
            log.warn("无法写入临时文件: {}", e.getMessage());
        }
    }
}