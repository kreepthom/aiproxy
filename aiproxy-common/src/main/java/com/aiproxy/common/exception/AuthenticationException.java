package com.aiproxy.common.exception;

public class AuthenticationException extends RelayException {
    
    public AuthenticationException(String message) {
        super(message, "AUTH_ERROR", 401);
    }
    
    public AuthenticationException(String message, Throwable cause) {
        super(message, "AUTH_ERROR", 401, cause);
    }
}