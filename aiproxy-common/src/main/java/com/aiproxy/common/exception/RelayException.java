package com.aiproxy.common.exception;

public class RelayException extends RuntimeException {
    
    private final String errorCode;
    private final int httpStatus;
    
    public RelayException(String message) {
        this(message, "RELAY_ERROR", 500);
    }
    
    public RelayException(String message, Throwable cause) {
        this(message, "RELAY_ERROR", 500, cause);
    }
    
    public RelayException(String message, String errorCode, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
    
    public RelayException(String message, String errorCode, int httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public int getHttpStatus() {
        return httpStatus;
    }
}