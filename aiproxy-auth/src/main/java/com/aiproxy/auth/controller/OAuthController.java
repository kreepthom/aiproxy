package com.aiproxy.auth.controller;

import com.aiproxy.auth.service.AccountService;
import com.aiproxy.auth.service.ClaudeOAuthService;
import com.aiproxy.common.model.ClaudeAccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/oauth")
@Slf4j
public class OAuthController {

    private final ClaudeOAuthService oauthService;
    private final AccountService accountService;

    public OAuthController(ClaudeOAuthService oauthService, AccountService accountService) {
        this.oauthService = oauthService;
        this.accountService = accountService;
    }

    @GetMapping("/authorize")
    public Mono<Map<String, Object>> generateAuthorizationUrl() {
        return oauthService.generateAuthorizationUrl()
            .map(result -> {
                log.info("Generated authorization URL for Claude OAuth");
                return Map.<String, Object>of(
                    "authorization_url", result.get("url"),
                    "code_verifier", result.get("codeVerifier"),
                    "message", "Please visit the authorization URL and copy the code after authorization"
                );
            });
    }

    @PostMapping("/token")
    public Mono<Map<String, Object>> exchangeCodeForToken(@RequestBody Map<String, String> request) {
        String code = request.get("code");
        String codeVerifier = request.get("code_verifier");
        String accountId = request.get("account_id");
        boolean updateExisting = Boolean.parseBoolean(request.getOrDefault("update_existing", "false"));

        if (code == null || codeVerifier == null) {
            return Mono.just(Map.<String, Object>of(
                "success", false,
                "error", "Missing required parameters: code and code_verifier"
            ));
        }

        log.info("Exchanging authorization code for tokens");
        return oauthService.exchangeCodeForToken(code, codeVerifier)
            .flatMap(tokens -> {
                String accessToken = tokens.get("access_token").toString();
                String refreshToken = tokens.get("refresh_token").toString();
                Integer expiresIn = (Integer) tokens.get("expires_in");
                
                // Extract account info directly from token response
                Map<String, Object> accountInfo = (Map<String, Object>) tokens.get("account");
                String email = accountInfo != null && accountInfo.get("email_address") != null 
                    ? accountInfo.get("email_address").toString() 
                    : "unknown@claude.ai";
                
                // Extract organization info
                Map<String, Object> orgInfo = (Map<String, Object>) tokens.get("organization");
                String orgName = orgInfo != null && orgInfo.get("name") != null 
                    ? orgInfo.get("name").toString() 
                    : "Unknown Organization";
                
                log.info("Account email: {}, Organization: {}", email, orgName);
                
                // 如果是更新现有账号
                if (updateExisting && accountId != null) {
                    return accountService.getAccountById(accountId)
                        .flatMap(existingAccount -> {
                            // 更新现有账号的令牌
                            existingAccount.setAccessToken(accessToken);
                            existingAccount.setRefreshToken(refreshToken);
                            existingAccount.setTokenExpiresAt(java.time.LocalDateTime.now().plusSeconds(expiresIn));
                            existingAccount.setEnabled(true);
                            existingAccount.setStatus("ACTIVE");
                            log.info("Updating existing account {} with new tokens", existingAccount.getEmail());
                            return accountService.saveAccount(existingAccount);
                        })
                        .flatMap(updatedAccount -> 
                            accountService.getAllAccounts().collectList()
                                .map(accounts -> Map.<String, Object>of(
                                    "success", true,
                                    "access_token", accessToken,
                                    "refresh_token", refreshToken,
                                    "expires_in", expiresIn,
                                    "email", email,
                                    "organization", orgName,
                                    "accounts", accounts,
                                    "updated_account", updatedAccount
                                ))
                        );
                } else {
                    // 创建新账号
                    ClaudeAccount account = ClaudeAccount.builder()
                        .id(UUID.randomUUID().toString())
                        .email(email)
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .tokenExpiresAt(java.time.LocalDateTime.now().plusSeconds(expiresIn))
                        .createdAt(java.time.LocalDateTime.now())
                        .enabled(true)
                        .status("ACTIVE")
                        .totalRequests(0L)
                        .totalTokens(0L)
                        .build();

                    return accountService.saveAccount(account)
                        .flatMap(savedAccount -> 
                            accountService.getAllAccounts().collectList()
                                .map(accounts -> Map.<String, Object>of(
                                    "success", true,
                                    "access_token", accessToken,
                                    "refresh_token", refreshToken,
                                    "expires_in", expiresIn,
                                    "email", email,
                                    "organization", orgName,
                                    "accounts", accounts,
                                    "new_account", savedAccount
                                ))
                        );
                }
            })
            .onErrorReturn(Map.<String, Object>of(
                "success", false,
                "error", "Failed to exchange code for tokens"
            ));
    }

    @PostMapping("/refresh")
    public Mono<Map<String, Object>> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refresh_token");

        if (refreshToken == null) {
            return Mono.just(Map.<String, Object>of(
                "success", false,
                "error", "Missing refresh_token"
            ));
        }

        return oauthService.refreshAccessToken(refreshToken)
            .map(tokens -> Map.<String, Object>of(
                "success", true,
                "access_token", tokens.get("access_token"),
                "expires_in", tokens.get("expires_in")
            ))
            .onErrorReturn(Map.<String, Object>of(
                "success", false,
                "error", "Failed to refresh token"
            ));
    }

    @GetMapping("/accounts")
    public Flux<ClaudeAccount> getAccounts() {
        return accountService.getAllAccounts();
    }

    @GetMapping("/accounts/{id}")
    public Mono<ClaudeAccount> getAccount(@PathVariable String id) {
        return accountService.getAccountById(id);
    }

    @PutMapping("/accounts/{id}/status")
    public Mono<Map<String, Object>> updateAccountStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {
        String status = request.get("status");
        Boolean enabled = Boolean.parseBoolean(request.getOrDefault("enabled", "true"));

        return accountService.updateAccountStatus(id, status, enabled)
            .map(account -> Map.<String, Object>of(
                "success", true,
                "account", account
            ))
            .onErrorReturn(Map.<String, Object>of(
                "success", false,
                "error", "Failed to update account status"
            ));
    }

    @DeleteMapping("/accounts/{id}")
    public Mono<Map<String, Object>> deleteAccount(@PathVariable String id) {
        return accountService.deleteAccount(id)
            .then(Mono.just(Map.<String, Object>of(
                "success", true,
                "message", "Account deleted successfully"
            )))
            .onErrorReturn(Map.<String, Object>of(
                "success", false,
                "error", "Failed to delete account"
            ));
    }
}
