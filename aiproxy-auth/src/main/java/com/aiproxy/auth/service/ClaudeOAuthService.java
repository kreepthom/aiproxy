package com.aiproxy.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@Service
@Slf4j
public class ClaudeOAuthService {
    
    private static final String CLAUDE_CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e";
    private static final String REDIRECT_URI = "https://console.anthropic.com/oauth/code/callback";
    private static final String AUTHORIZE_URL = "https://claude.ai/oauth/authorize";
    private static final String TOKEN_URL = "https://console.anthropic.com/v1/oauth/token";  // Fixed: correct token endpoint
    private static final String API_BASE_URL = "https://console.anthropic.com/api";
    
    private final WebClient webClient;
    
    public ClaudeOAuthService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }
    
    public Mono<Map<String, String>> generateAuthorizationUrl() {
        return Mono.fromCallable(() -> {
            try {
                // Generate PKCE parameters (32字节随机数，base64url编码)
                String codeVerifier = generateCodeVerifier();
                String codeChallenge = generateCodeChallenge(codeVerifier);
                
                // Generate state parameter for CSRF protection (32字节随机数，base64url编码)
                String state = generateState();
                
                // Manually build the URL with proper encoding
                // URLEncoder.encode will encode everything, then we need to replace %20 with +
                String encodedRedirectUri = URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8.toString());
                // Note: org:create_api_key is included in URL but not in the actual OAuth request body
                String encodedScope = URLEncoder.encode("org:create_api_key user:profile user:inference", StandardCharsets.UTF_8.toString())
                    .replace("%20", "+");  // In query parameters, spaces should be + not %20
                
                // Build the URL manually to ensure correct encoding
                String authUrl = String.format(
                    "%s?code=true&client_id=%s&response_type=code&redirect_uri=%s&scope=%s&code_challenge=%s&code_challenge_method=S256&state=%s",
                    AUTHORIZE_URL,
                    CLAUDE_CLIENT_ID,
                    encodedRedirectUri,
                    encodedScope,
                    codeChallenge,
                    state
                );
                
                log.debug("Generated auth URL: {}", authUrl);
                log.debug("Code verifier (save this): {}", codeVerifier);
                log.debug("State parameter: {}", state);
                
                return Map.of(
                    "url", authUrl,
                    "codeVerifier", codeVerifier,
                    "state", state
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate authorization URL", e);
            }
        });
    }
    
    public Mono<Map<String, Object>> exchangeCodeForToken(String code, String codeVerifier) {
        // Parse the input - could be just code, code#state, or full callback URL
        String actualCode = parseAuthorizationCode(code);
        
        // Extract state from the input if present (code#state format)
        String state = null;
        if (code.contains("#")) {
            String[] parts = code.split("#");
            if (parts.length > 1) {
                state = parts[1];
            }
        }
        
        log.info("Parsing authorization input: {}", code);
        log.info("Extracted code: {}", actualCode);
        log.info("Extracted state: {}", state);
        log.info("Code verifier: {}", codeVerifier);
        
        // Build request body as JSON - INCLUDING STATE PARAMETER!
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("grant_type", "authorization_code");
        requestBody.put("code", actualCode);
        requestBody.put("client_id", CLAUDE_CLIENT_ID);
        requestBody.put("redirect_uri", REDIRECT_URI);
        requestBody.put("code_verifier", codeVerifier);
        if (state != null && !state.isEmpty()) {
            requestBody.put("state", state);  // CRITICAL: Include state parameter
        }
        
        log.info("Token exchange request - URL: {}", TOKEN_URL);
        log.info("Request body (JSON): {}", requestBody);
        
        // Use proper headers as per the Node.js implementation
        return webClient.post()
            .uri(TOKEN_URL)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/plain, */*")
            .header("User-Agent", "claude-cli/1.0.56 (external, cli)")  // Important!
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", "https://claude.ai/")
            .header("Origin", "https://claude.ai")
            .bodyValue(requestBody)  // This will auto-convert to proper JSON
            .retrieve()
            .onStatus(status -> !status.is2xxSuccessful(), 
                clientResponse -> clientResponse.bodyToMono(String.class)
                    .flatMap(errorBody -> {
                        log.error("Token exchange failed with status {}: {}", clientResponse.statusCode(), errorBody);
                        return Mono.error(new RuntimeException("Token exchange failed: " + errorBody));
                    }))
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .doOnSuccess(response -> {
                log.info("Successfully exchanged code for tokens!");
                log.info("Token response: {}", response);
            })
            .doOnError(error -> log.error("Failed to exchange code: ", error));
    }
    
    public Mono<Map<String, Object>> refreshAccessToken(String refreshToken) {
        return webClient.post()
            .uri(TOKEN_URL)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .bodyValue(String.format(
                "grant_type=refresh_token&refresh_token=%s&client_id=%s",
                refreshToken,
                CLAUDE_CLIENT_ID
            ))
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .doOnSuccess(response -> log.info("Successfully refreshed access token"))
            .doOnError(error -> log.error("Failed to refresh token: ", error));
    }
    
    private String generateCodeVerifier() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] codeVerifier = new byte[32];
        secureRandom.nextBytes(codeVerifier);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifier);
    }
    
    private String generateState() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] state = new byte[32];  // Changed from 16 to 32 bytes - Claude requires 32 bytes for state
        secureRandom.nextBytes(state);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(state);
    }
    
    private String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate code challenge", e);
        }
    }
    
    public Mono<Map<String, Object>> getCurrentUser(String accessToken) {
        return webClient.get()
            .uri(API_BASE_URL + "/users/me")
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .doOnSuccess(response -> log.info("Successfully fetched current user info"))
            .doOnError(error -> log.error("Failed to fetch user info: ", error));
    }
    
    public Flux<Map<String, Object>> getOrganizations(String accessToken) {
        return webClient.get()
            .uri(API_BASE_URL + "/organizations")
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .bodyToFlux(new ParameterizedTypeReference<Map<String, Object>>() {})
            .doOnNext(org -> log.info("Found organization: {}", org.get("name")))
            .doOnError(error -> log.error("Failed to fetch organizations: ", error));
    }
    
    public Flux<Map<String, Object>> getApiKeys(String accessToken, String organizationId) {
        return webClient.get()
            .uri(API_BASE_URL + "/organizations/" + organizationId + "/api_keys")
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .bodyToFlux(new ParameterizedTypeReference<Map<String, Object>>() {})
            .doOnNext(key -> log.info("Found API key: {}", key.get("name")))
            .doOnError(error -> log.error("Failed to fetch API keys: ", error));
    }
    
    private String parseAuthorizationCode(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Authorization code input is empty");
        }
        
        // Remove any whitespace
        String trimmed = input.trim();
        
        // Check if it's a full callback URL
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            try {
                // Parse URL and extract code parameter
                String url = trimmed;
                int codeIndex = url.indexOf("code=");
                if (codeIndex != -1) {
                    String codeParam = url.substring(codeIndex + 5);
                    int ampIndex = codeParam.indexOf("&");
                    if (ampIndex != -1) {
                        return codeParam.substring(0, ampIndex);
                    }
                    int hashIndex = codeParam.indexOf("#");
                    if (hashIndex != -1) {
                        return codeParam.substring(0, hashIndex);
                    }
                    return codeParam;
                }
            } catch (Exception e) {
                log.warn("Failed to parse callback URL: {}", e.getMessage());
            }
        }
        
        // Check if it contains # (code#state format)
        if (trimmed.contains("#")) {
            return trimmed.substring(0, trimmed.indexOf("#"));
        }
        
        // Assume it's just the code
        return trimmed;
    }
    
    private Mono<Map<String, Object>> tryFormUrlEncoded(String code, String codeVerifier) {
        // Build form-urlencoded body
        String formBody = String.format(
            "grant_type=authorization_code&code=%s&client_id=%s&redirect_uri=%s&code_verifier=%s",
            code,  // Don't URL encode the code itself
            CLAUDE_CLIENT_ID,
            URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8),
            codeVerifier  // Don't URL encode the code_verifier
        );
        
        log.info("Retrying with form-urlencoded format");
        log.info("Request body: {}", formBody);
        
        return webClient.post()
            .uri(TOKEN_URL)
            .header("Accept", "application/json")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .bodyValue(formBody)
            .retrieve()
            .onStatus(status -> !status.is2xxSuccessful(), 
                clientResponse -> clientResponse.bodyToMono(String.class)
                    .flatMap(errorBody -> {
                        log.error("Form-urlencoded also failed with status {}: {}", clientResponse.statusCode(), errorBody);
                        
                        // Log additional debugging info
                        log.error("Debug info:");
                        log.error("  - Code: {}", code);
                        log.error("  - Code Verifier: {}", codeVerifier);
                        log.error("  - Client ID: {}", CLAUDE_CLIENT_ID);
                        log.error("  - Redirect URI: {}", REDIRECT_URI);
                        log.error("  - Token URL: {}", TOKEN_URL);
                        
                        return Mono.error(new RuntimeException("Token exchange failed with both formats: " + errorBody));
                    }))
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .doOnSuccess(response -> log.info("Successfully exchanged code for tokens using form-urlencoded: {}", response))
            .doOnError(error -> log.error("Failed with form-urlencoded format: ", error));
    }
}