package com.aiproxy.admin.controller;

import com.aiproxy.auth.service.AdminAuthService;
import com.aiproxy.common.entity.AdminUserEntity;
import com.aiproxy.common.repository.AdminUserRepository;
import com.aiproxy.common.service.SystemSettingService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/settings")
@Slf4j
public class SettingsController {
    
    private final SystemSettingService settingService;
    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuthService adminAuthService;
    
    public SettingsController(SystemSettingService settingService,
                            AdminUserRepository adminUserRepository,
                            PasswordEncoder passwordEncoder,
                            AdminAuthService adminAuthService) {
        this.settingService = settingService;
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminAuthService = adminAuthService;
    }
    
    /**
     * 获取常规设置
     */
    @GetMapping("/general")
    public Mono<Map<String, Object>> getGeneralSettings() {
        log.info("Getting general settings");
        return settingService.getSettings("general");
    }
    
    /**
     * 更新常规设置
     */
    @PutMapping("/general")
    public Mono<Map<String, Object>> updateGeneralSettings(@RequestBody Map<String, Object> settings) {
        log.info("Updating general settings: {}", settings);
        return settingService.saveSettings("general", settings)
            .map(response -> {
                // 如果保存成功，添加更新后的系统名称
                if ((boolean) response.getOrDefault("success", false)) {
                    response.put("systemName", settings.get("systemName"));
                }
                return response;
            });
    }
    
    /**
     * 获取安全设置
     */
    @GetMapping("/security")
    public Mono<Map<String, Object>> getSecuritySettings() {
        log.info("Getting security settings");
        return settingService.getSettings("security");
    }
    
    /**
     * 更新安全设置
     */
    @PutMapping("/security")
    public Mono<Map<String, Object>> updateSecuritySettings(@RequestBody Map<String, Object> settings) {
        log.info("Updating security settings: {}", settings);
        return settingService.saveSettings("security", settings);
    }
    
    /**
     * 获取速率限制设置
     */
    @GetMapping("/rate-limit")
    public Mono<Map<String, Object>> getRateLimitSettings() {
        log.info("Getting rate limit settings");
        return settingService.getSettings("rate-limit");
    }
    
    /**
     * 更新速率限制设置
     */
    @PutMapping("/rate-limit")
    public Mono<Map<String, Object>> updateRateLimitSettings(@RequestBody Map<String, Object> settings) {
        log.info("Updating rate limit settings: {}", settings);
        return settingService.saveSettings("rate-limit", settings);
    }
    
    /**
     * 获取当前系统名称
     */
    @GetMapping("/system-name")
    public Mono<Map<String, String>> getSystemName() {
        Map<String, String> response = new HashMap<>();
        response.put("systemName", settingService.getSystemName());
        return Mono.just(response);
    }
    
    /**
     * 修改密码接口
     */
    @PostMapping("/change-password")
    public Mono<Map<String, Object>> changePassword(@RequestBody PasswordChangeRequest request,
                                                   ServerWebExchange exchange) {
        log.info("Changing password for admin user");
        
        // 从token获取当前用户名
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "未授权的请求");
            return Mono.just(response);
        }
        
        // 这里暂时使用默认的admin用户，后续可以从token中解析用户名
        String username = "admin";
        
        return Mono.fromCallable(() -> adminUserRepository.findByUsername(username))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(optionalUser -> {
                Map<String, Object> response = new HashMap<>();
                
                if (optionalUser.isEmpty()) {
                    // 如果用户不存在，可能是首次运行，检查默认密码
                    if ("admin123".equals(request.getOldPassword())) {
                        // 创建新的管理员用户
                        AdminUserEntity newAdmin = AdminUserEntity.builder()
                            .id(java.util.UUID.randomUUID().toString())
                            .username(username)
                            .passwordHash(passwordEncoder.encode(request.getNewPassword()))
                            .isActive(true)
                            .build();
                        
                        return Mono.fromCallable(() -> adminUserRepository.save(newAdmin))
                            .subscribeOn(Schedulers.boundedElastic())
                            .map(saved -> {
                                response.put("success", true);
                                response.put("message", "密码修改成功");
                                return response;
                            });
                    } else {
                        response.put("success", false);
                        response.put("message", "当前密码不正确");
                        return Mono.just(response);
                    }
                }
                
                AdminUserEntity user = optionalUser.get();
                
                // 验证旧密码
                if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
                    response.put("success", false);
                    response.put("message", "当前密码不正确");
                    return Mono.just(response);
                }
                
                // 更新密码
                user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
                
                return Mono.fromCallable(() -> adminUserRepository.save(user))
                    .subscribeOn(Schedulers.boundedElastic())
                    .map(saved -> {
                        log.info("Password changed successfully for user: {}", username);
                        response.put("success", true);
                        response.put("message", "密码修改成功");
                        return response;
                    });
            })
            .onErrorResume(error -> {
                log.error("Failed to change password", error);
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "密码修改失败: " + error.getMessage());
                return Mono.just(response);
            });
    }
    
    @Data
    public static class PasswordChangeRequest {
        private String oldPassword;
        private String newPassword;
    }
}