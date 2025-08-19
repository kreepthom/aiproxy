package com.aiproxy.common.enums;

public enum AIProvider {
    CLAUDE("Claude", "Anthropic Claude AI"),
    OPENAI("OpenAI", "OpenAI ChatGPT"),
    GEMINI("Gemini", "Google Gemini AI");
    
    private final String name;
    private final String description;
    
    AIProvider(String name, String description) {
        this.name = name;
        this.description = description;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
}