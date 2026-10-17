package com.khayaal.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.rate-limit.sensitive")
public record RateLimitProperties(int maximumRequestsPerMinute, Duration keyTtl, boolean failOpen) {
    public RateLimitProperties {
        if (maximumRequestsPerMinute < 1) throw new IllegalArgumentException("maximumRequestsPerMinute must be positive");
        if (keyTtl == null || keyTtl.isNegative() || keyTtl.isZero()) throw new IllegalArgumentException("keyTtl must be positive");
    }
}
