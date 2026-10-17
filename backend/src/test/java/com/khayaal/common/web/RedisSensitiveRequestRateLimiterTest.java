package com.khayaal.common.web;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
class RedisSensitiveRequestRateLimiterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void allowsUpToTheConfiguredLimitAndThenRejects() {
        var count = new AtomicInteger();
        var limiter = new RedisSensitiveRequestRateLimiter((key, ttl) -> (long) count.incrementAndGet(),
                new RateLimitProperties(20, Duration.ofMinutes(2), true), CLOCK, new SimpleMeterRegistry());

        for (int request = 0; request < 20; request++) {
            assertThat(limiter.allow("127.0.0.1:/api/auth/login")).isTrue();
        }
        assertThat(limiter.allow("127.0.0.1:/api/auth/login")).isFalse();
    }

    @Test
    void followsTheConfiguredFailurePolicyWhenRedisIsUnavailable() {
        java.util.function.BiFunction<String, Duration, Long> unavailable = (key, ttl) -> {
            throw new IllegalStateException("offline");
        };

        var failOpen = new RedisSensitiveRequestRateLimiter(unavailable,
                new RateLimitProperties(20, Duration.ofMinutes(2), true), CLOCK, new SimpleMeterRegistry());
        var failClosed = new RedisSensitiveRequestRateLimiter(unavailable,
                new RateLimitProperties(20, Duration.ofMinutes(2), false), CLOCK, new SimpleMeterRegistry());

        assertThat(failOpen.allow("client")).isTrue();
        assertThat(failClosed.allow("client")).isFalse();
    }
}
