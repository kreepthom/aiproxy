package com.aiproxy.common.service;

import com.aiproxy.common.entity.SystemSettingEntity;
import com.aiproxy.common.repository.SystemSettingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SystemSettingService {
    
    private final SystemSettingRepository settingRepository;
    
    // 缓存系统设置
    private Map<String, String> settingsCache = new HashMap<>();
    
    public SystemSettingService(SystemSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
        loadSettingsToCache();
    }
    
    private void loadSettingsToCache() {
        try {
            List<SystemSettingEntity> settings = settingRepository.findAll();
            for (SystemSettingEntity setting : settings) {
                settingsCache.put(setting.getSettingKey(), setting.getSettingValue());
            }
            log.info("Loaded {} settings to cache", settings.size());
        } catch (Exception e) {
            log.error("Failed to load settings to cache", e);
            // 初始化默认设置
            initDefaultSettings();
        }
    }
    
    private void initDefaultSettings() {
        // 默认设置
        settingsCache.put("system.name", "Claude Relay");
        settingsCache.put("claude.base.url", "https://api.anthropic.com");
        settingsCache.put("gemini.base.url", "https://generativelanguage.googleapis.com");
        settingsCache.put("session.timeout", "24");
        settingsCache.put("enable.logging", "true");
        settingsCache.put("enable.two.factor", "false");
        settingsCache.put("max.login.attempts", "5");
        settingsCache.put("enable.rate.limit", "true");
        settingsCache.put("default.requests.per.minute", "60");
        settingsCache.put("default.requests.per.hour", "1000");
        settingsCache.put("default.tokens.per.day", "1000000");
    }
    
    @Transactional
    public Mono<Map<String, Object>> saveSettings(String group, Map<String, Object> settings) {
        return Mono.fromCallable(() -> {
            for (Map.Entry<String, Object> entry : settings.entrySet()) {
                String key = convertToKey(group, entry.getKey());
                String value = String.valueOf(entry.getValue());
                
                SystemSettingEntity entity = settingRepository.findBySettingKey(key)
                    .orElse(new SystemSettingEntity());
                
                entity.setSettingKey(key);
                entity.setSettingValue(value);
                entity.setSettingGroup(group);
                
                settingRepository.save(entity);
                settingsCache.put(key, value); // 更新缓存
                
                log.info("Saved setting: {} = {}", key, value);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "设置已保存");
            response.put("data", settings);
            
            return response;
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    public Mono<Map<String, Object>> getSettings(String group) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>();
            
            // 从缓存获取设置
            if ("general".equals(group)) {
                // 特殊处理general组的设置
                addToResultIfExists(result, "system.name", "systemName");
                addToResultIfExists(result, "claude.base.url", "claudeBaseUrl");
                addToResultIfExists(result, "gemini.base.url", "geminiBaseUrl");
                addToResultIfExists(result, "session.timeout", "sessionTimeout");
                addToResultIfExists(result, "enable.logging", "enableLogging");
            } else if ("security".equals(group)) {
                addToResultIfExists(result, "enable.two.factor", "enableTwoFactor");
                addToResultIfExists(result, "allowed.ips", "allowedIps");
                addToResultIfExists(result, "max.login.attempts", "maxLoginAttempts");
            } else if ("rate-limit".equals(group)) {
                addToResultIfExists(result, "enable.rate.limit", "enableRateLimit");
                addToResultIfExists(result, "default.requests.per.minute", "defaultRequestsPerMinute");
                addToResultIfExists(result, "default.requests.per.hour", "defaultRequestsPerHour");
                addToResultIfExists(result, "default.tokens.per.day", "defaultTokensPerDay");
                addToResultIfExists(result, "burst.size", "burstSize");
            }
            
            // 如果缓存为空，返回默认值
            if (result.isEmpty()) {
                result = getDefaultSettings(group);
            }
            
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    private void addToResultIfExists(Map<String, Object> result, String key, String fieldName) {
        if (settingsCache.containsKey(key)) {
            result.put(fieldName, parseValue(settingsCache.get(key)));
        }
    }
    
    public String getSetting(String key) {
        return settingsCache.getOrDefault(key, "");
    }
    
    public String getSystemName() {
        return settingsCache.getOrDefault("system.name", "Claude Relay");
    }
    
    private String convertToKey(String group, String fieldName) {
        // 特殊处理一些字段
        if ("general".equals(group)) {
            switch (fieldName) {
                case "systemName":
                    return "system.name";
                case "claudeBaseUrl":
                    return "claude.base.url";
                case "geminiBaseUrl":
                    return "gemini.base.url";
                case "sessionTimeout":
                    return "session.timeout";
                case "enableLogging":
                    return "enable.logging";
            }
        } else if ("security".equals(group)) {
            switch (fieldName) {
                case "enableTwoFactor":
                    return "enable.two.factor";
                case "allowedIps":
                    return "allowed.ips";
                case "maxLoginAttempts":
                    return "max.login.attempts";
            }
        } else if ("rate-limit".equals(group)) {
            switch (fieldName) {
                case "enableRateLimit":
                    return "enable.rate.limit";
                case "defaultRequestsPerMinute":
                    return "default.requests.per.minute";
                case "defaultRequestsPerHour":
                    return "default.requests.per.hour";
                case "defaultTokensPerDay":
                    return "default.tokens.per.day";
                case "burstSize":
                    return "burst.size";
            }
        }
        
        // 默认转换逻辑
        String prefix = getGroupPrefix(group);
        String key = fieldName.replaceAll("([A-Z])", ".$1").toLowerCase();
        return prefix + "." + key;
    }
    
    private String convertFromKey(String key) {
        // 特殊处理一些key
        switch (key) {
            case "system.name":
                return "systemName";
            case "claude.base.url":
                return "claudeBaseUrl";
            case "gemini.base.url":
                return "geminiBaseUrl";
            case "session.timeout":
                return "sessionTimeout";
            case "enable.logging":
                return "enableLogging";
            case "enable.two.factor":
                return "enableTwoFactor";
            case "allowed.ips":
                return "allowedIps";
            case "max.login.attempts":
                return "maxLoginAttempts";
            case "enable.rate.limit":
                return "enableRateLimit";
            case "default.requests.per.minute":
                return "defaultRequestsPerMinute";
            case "default.requests.per.hour":
                return "defaultRequestsPerHour";
            case "default.tokens.per.day":
                return "defaultTokensPerDay";
            case "burst.size":
                return "burstSize";
        }
        
        // 默认转换逻辑
        String[] parts = key.split("\\.");
        if (parts.length < 2) return key;
        
        StringBuilder result = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            if (i == 1) {
                result.append(parts[i]);
            } else {
                result.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) {
                    result.append(parts[i].substring(1));
                }
            }
        }
        return result.toString();
    }
    
    private String getGroupPrefix(String group) {
        switch (group) {
            case "general":
                return "system";
            case "security":
                return "security";
            case "rate-limit":
                return "rate.limit";
            default:
                return group;
        }
    }
    
    private Object parseValue(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }
    
    private Map<String, Object> getDefaultSettings(String group) {
        Map<String, Object> defaults = new HashMap<>();
        
        switch (group) {
            case "general":
                defaults.put("systemName", "Claude Relay");
                defaults.put("claudeBaseUrl", "https://api.anthropic.com");
                defaults.put("geminiBaseUrl", "https://generativelanguage.googleapis.com");
                defaults.put("sessionTimeout", 24);
                defaults.put("enableLogging", true);
                break;
            case "security":
                defaults.put("enableTwoFactor", false);
                defaults.put("allowedIps", "");
                defaults.put("maxLoginAttempts", 5);
                break;
            case "rate-limit":
                defaults.put("enableRateLimit", true);
                defaults.put("defaultRequestsPerMinute", 60);
                defaults.put("defaultRequestsPerHour", 1000);
                defaults.put("defaultTokensPerDay", 1000000);
                defaults.put("burstSize", 10);
                break;
        }
        
        return defaults;
    }
}