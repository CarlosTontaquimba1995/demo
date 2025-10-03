package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security.oauth2.token")
public class OAuth2TokenProperties {
    private String url;
    private String clientId;
    private String username;
    private String password;
    private long refreshOffsetSeconds;
    private long refreshCheckIntervalMs;
}
