package com.aiproxy.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication(scanBasePackages = "com.aiproxy")
public class AiProxyApplication {
    
    @PostConstruct
    public void init() {
        // 设置默认时区为上海时间
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
    }
    
    public static void main(String[] args) {
        // 设置默认时区为上海时间
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(AiProxyApplication.class, args);
    }
}