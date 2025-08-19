package com.aiproxy.api.config;

import com.aiproxy.common.entity.AdminUserEntity;
import com.aiproxy.common.repository.AdminUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.UUID;

@Configuration
@Slf4j
public class AdminInitConfig implements ApplicationRunner {
    
    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    @Value("${relay.admin.auto-create:true}")
    private boolean autoCreateAdmin;
    
    @Value("${relay.admin.username:admin}")
    private String adminUsername;
    
    @Value("${relay.admin.password:}")
    private String adminPassword;
    
    public AdminInitConfig(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=== Admin Initialization Starting ===");
        log.info("Auto-create admin: {}", autoCreateAdmin);
        log.info("Admin username: {}", adminUsername);
        log.info("Admin password configured: {}", !adminPassword.isEmpty());
        
        if (!autoCreateAdmin) {
            log.info("Auto-create admin is disabled");
            return;
        }
        
        try {
            // 检查管理员是否已存在
            boolean exists = adminUserRepository.existsByUsername(adminUsername);
            log.info("Admin user exists: {}", exists);
            
            if (exists) {
                log.info("Admin user '{}' already exists in database", adminUsername);
                return;
            }
            
            // 生成密码
            String finalPassword = adminPassword;
            if (finalPassword == null || finalPassword.isEmpty()) {
                finalPassword = UUID.randomUUID().toString().substring(0, 12);
                log.info("Generated admin password: {}", finalPassword);
            }
            
            // 创建管理员
            AdminUserEntity admin = AdminUserEntity.builder()
                .id(UUID.randomUUID().toString())
                .username(adminUsername)
                .passwordHash(passwordEncoder.encode(finalPassword))
                .email(adminUsername + "@relay.local")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
            
            adminUserRepository.save(admin);
            
            log.info("========================================");
            log.info("Admin user created successfully!");
            log.info("Username: {}", adminUsername);
            if (adminPassword == null || adminPassword.isEmpty()) {
                log.info("Password: {}", finalPassword);
                log.info("Please save this password securely!");
            } else {
                log.info("Password: [configured in application.yml]");
            }
            log.info("========================================");
            
        } catch (Exception e) {
            log.error("Failed to initialize admin user", e);
        }
    }
}