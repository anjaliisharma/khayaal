package com.khayaal.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security")
public record SecurityProperties(String jwtSecret, long accessTokenMinutes, long refreshTokenDays, boolean secureCookie) { }
