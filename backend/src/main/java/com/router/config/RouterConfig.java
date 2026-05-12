package com.router.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "router")
public class RouterConfig {
    private String baseUrl;
    private String username;
    private String password;
    private String loginPasswordPlain;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getLoginPasswordPlain() { return loginPasswordPlain; }
    public void setLoginPasswordPlain(String loginPasswordPlain) { this.loginPasswordPlain = loginPasswordPlain; }
}
