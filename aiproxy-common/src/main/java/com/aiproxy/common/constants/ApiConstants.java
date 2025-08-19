package com.aiproxy.common.constants;

public class ApiConstants {
    
    public static final String CLAUDE_BASE_URL = "https://api.anthropic.com";
    public static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com";
    
    public static final String CLAUDE_VERSION_HEADER = "anthropic-version";
    public static final String CLAUDE_VERSION = "2023-06-01";
    
    public static final String AUTH_HEADER = "Authorization";
    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String BEARER_PREFIX = "Bearer ";
    
    public static final String CONTENT_TYPE_SSE = "text/event-stream";
    public static final String CONTENT_TYPE_JSON = "application/json";
    
    public static final int DEFAULT_TIMEOUT_SECONDS = 300;
    public static final int DEFAULT_MAX_RETRIES = 3;
    
    private ApiConstants() {}
}