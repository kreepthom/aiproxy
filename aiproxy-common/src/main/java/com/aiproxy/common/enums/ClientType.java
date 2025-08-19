package com.aiproxy.common.enums;

public enum ClientType {
    CLAUDE_CODE("Claude Code", "claude-code"),
    GEMINI_CLI("Gemini CLI", "gemini-cli"),
    CHERRY_STUDIO("Cherry Studio", "cherry-studio"),
    CUSTOM("Custom", "custom"),
    UNKNOWN("Unknown", "unknown");
    
    private final String displayName;
    private final String identifier;
    
    ClientType(String displayName, String identifier) {
        this.displayName = displayName;
        this.identifier = identifier;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getIdentifier() {
        return identifier;
    }
    
    public static ClientType fromUserAgent(String userAgent) {
        if (userAgent == null) {
            return UNKNOWN;
        }
        
        String lowerCase = userAgent.toLowerCase();
        for (ClientType type : values()) {
            if (lowerCase.contains(type.identifier)) {
                return type;
            }
        }
        return CUSTOM;
    }
}