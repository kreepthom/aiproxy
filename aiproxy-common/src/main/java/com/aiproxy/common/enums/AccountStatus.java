package com.aiproxy.common.enums;

public enum AccountStatus {
    ACTIVE("Active"),
    INACTIVE("Inactive"),
    DISABLED("Disabled"),  // 添加 DISABLED 状态以兼容
    EXPIRED("Expired"),
    SUSPENDED("Suspended"),
    ERROR("Error");
    
    private final String description;
    
    AccountStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}