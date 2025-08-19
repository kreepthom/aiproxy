package com.aiproxy.admin.controller;

import com.aiproxy.auth.service.AccountService;
import com.aiproxy.auth.service.ClaudeOAuthService;
import com.aiproxy.common.model.ClaudeAccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/accounts")
@Slf4j
public class AccountController {
    
    private final AccountService accountService;
    private final ClaudeOAuthService oauthService;
    
    public AccountController(AccountService accountService, ClaudeOAuthService oauthService) {
        this.accountService = accountService;
        this.oauthService = oauthService;
    }
    
    @GetMapping("/authorize-url")
    public Mono<Map<String, Object>> getAuthorizeUrl() {
        log.info("=== AccountController.getAuthorizeUrl() called ===");
        return oauthService.generateAuthorizationUrl()
            .map(result -> {
                log.info("Generated Claude OAuth authorization URL");
                Map<String, Object> response = Map.of(
                    "success", true,
                    "authorization_url", result.get("url"),
                    "code_verifier", result.get("codeVerifier"),
                    "state", result.get("state"),
                    "instructions", "1. Visit the authorization URL\n" +
                                  "2. Login to Claude and authorize\n" +
                                  "3. Copy the authorization code from the callback page\n" +
                                  "4. Use POST /admin/accounts/add with the code and code_verifier"
                );
                log.info("=== Returning successful response from AccountController ===");
                return response;
            })
            .doOnError(error -> log.error("Error in getAuthorizeUrl: ", error));
    }
    
    @PostMapping("/add")
    public Mono<Map<String, Object>> addAccount(@RequestBody Map<String, String> request) {
        String code = request.get("code");
        String codeVerifier = request.get("code_verifier");
        String email = request.getOrDefault("email", "unknown@claude.ai");
        
        if (code == null || codeVerifier == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Missing required parameters: code and code_verifier");
            return Mono.just(errorResponse);
        }
        
        return accountService.createAccountFromAuthCode(code, codeVerifier, email)
            .<Map<String, Object>>map(account -> {
                Map<String, Object> successResponse = new HashMap<>();
                successResponse.put("success", true);
                successResponse.put("message", "Account added successfully");
                successResponse.put("account_id", account.getId());
                successResponse.put("email", account.getEmail());
                return successResponse;
            })
            .onErrorResume(error -> {
                log.error("Failed to add account: ", error);
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("success", false);
                errorMap.put("error", "Failed to add account. Please check the authorization code.");
                return Mono.just(errorMap);
            });
    }
    
    @GetMapping
    public Flux<ClaudeAccount> listAccounts() {
        return accountService.getAllActiveAccounts();
    }
    
    @GetMapping("/{accountId}")
    public Mono<ClaudeAccount> getAccount(@PathVariable String accountId) {
        return accountService.getAccount(accountId);
    }
    
    @PostMapping("/{accountId}/refresh")
    public Mono<Map<String, Object>> refreshAccountToken(@PathVariable String accountId) {
        return accountService.getAccount(accountId)
            .flatMap(accountService::refreshAccountToken)
            .<Map<String, Object>>map(account -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Token refreshed successfully");
                response.put("expires_at", account.getTokenExpiresAt().toString());
                return response;
            })
            .switchIfEmpty(Mono.defer(() -> {
                Map<String, Object> notFoundMap = new HashMap<>();
                notFoundMap.put("success", false);
                notFoundMap.put("error", "Account not found");
                return Mono.just(notFoundMap);
            }));
    }
    
    @DeleteMapping("/{accountId}")
    public Mono<Map<String, Object>> deleteAccount(@PathVariable String accountId) {
        return accountService.deleteAccount(accountId)
            .map(success -> Map.of(
                "success", success,
                "message", success ? "Account deleted" : "Account not found"
            ));
    }
}