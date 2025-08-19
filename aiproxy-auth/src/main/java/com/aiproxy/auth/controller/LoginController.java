package com.aiproxy.auth.controller;

import com.aiproxy.auth.service.AdminAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@Slf4j
public class LoginController {
    
    private final AdminAuthService adminAuthService;
    
    public LoginController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }
    
    @PostMapping("/login")
    public Mono<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        
        if (username == null || password == null) {
            java.util.Map<String, Object> errorResponse = new java.util.HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "用户名和密码不能为空");
            return Mono.just(errorResponse);
        }
        
        return adminAuthService.login(username, password)
            .<Map<String, Object>>map(token -> {
                java.util.Map<String, Object> successResponse = new java.util.HashMap<>();
                successResponse.put("success", true);
                successResponse.put("message", "登录成功");
                successResponse.put("token", token);
                successResponse.put("expiresIn", 86400);  // 24小时
                return successResponse;
            })
            .switchIfEmpty(Mono.defer(() -> {
                java.util.Map<String, Object> errorMap = new java.util.HashMap<>();
                errorMap.put("success", false);
                errorMap.put("message", "用户名或密码错误");
                return Mono.just(errorMap);
            }));
    }
    
    @PostMapping("/logout")
    public Mono<Map<String, Object>> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token != null && token.startsWith("Bearer ")) {
            String actualToken = token.substring(7);
            return adminAuthService.logout(actualToken)
                .then(Mono.just(Map.of(
                    "success", true,
                    "message", "登出成功"
                )));
        }
        return Mono.just(Map.of(
            "success", true,
            "message", "已登出"
        ));
    }
    
    @GetMapping("/check")
    public Mono<Map<String, Object>> checkAuth(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            return Mono.just(Map.of(
                "authenticated", false,
                "message", "未登录"
            ));
        }
        
        String actualToken = token.substring(7);
        return adminAuthService.validateToken(actualToken)
            .map(valid -> {
                if (valid) {
                    return Map.of(
                        "authenticated", true,
                        "message", "已登录",
                        "username", "admin"
                    );
                } else {
                    return Map.of(
                        "authenticated", false,
                        "message", "登录已过期"
                    );
                }
            });
    }
}