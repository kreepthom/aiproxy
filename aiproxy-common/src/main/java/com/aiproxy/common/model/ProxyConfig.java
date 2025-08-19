package com.aiproxy.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxyConfig implements Serializable {
    
    private boolean enabled;
    private String type; // http, https, socks5
    private String host;
    private int port;
    private String username;
    private String password;
    
    public boolean requiresAuth() {
        return username != null && !username.isEmpty();
    }
}