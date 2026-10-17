package com.khayaal.common.web;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.function.BiFunction;

@Component
public class RedisSensitiveRequestRateLimiter implements SensitiveRequestRateLimiter {
    private static final Logger logger = LoggerFactory.getLogger(RedisSensitiveRequestRateLimiter.class);
    private static final DefaultRedisScript<Long> INCREMENT_AND_EXPIRE = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final BiFunction<String, Duration, Long> counter;
    private final RateLimitProperties properties;
    private final Clock clock;
    private final Counter allowed;
    private final Counter rejected;
    private final Counter unavailable;

    @Autowired
    public RedisSensitiveRequestRateLimiter(StringRedisTemplate redis, RateLimitProperties properties,
                                            Clock clock, MeterRegistry metrics) {
        this((key, ttl) -> redis.execute(INCREMENT_AND_EXPIRE, List.of(key), ttl.toMillis()),
                properties, clock, metrics);
    }

    RedisSensitiveRequestRateLimiter(BiFunction<String, Duration, Long> counter,
                                     RateLimitProperties properties, Clock clock, MeterRegistry metrics) {
        this.counter = counter;
        this.properties = properties;
        this.clock = clock;
        this.allowed = metrics.counter("khayaal.rate_limit.requests", "outcome", "allowed");
        this.rejected = metrics.counter("khayaal.rate_limit.requests", "outcome", "rejected");
        this.unavailable = metrics.counter("khayaal.rate_limit.requests", "outcome", "redis_unavailable");
    }

    @Override
    public boolean allow(String discriminator) {
        long minute = clock.instant().getEpochSecond() / 60;
        String key = "khayaal:rate-limit:sensitive:" + minute + ":" + sha256(discriminator);
        try {
            Long count = counter.apply(key, properties.keyTtl());
            boolean permitted = count != null && count <= properties.maximumRequestsPerMinute();
            (permitted ? allowed : rejected).increment();
            return permitted;
        } catch (RuntimeException exception) {
            unavailable.increment();
            logger.warn("Redis rate limiter unavailable; failOpen={}", properties.failOpen(), exception);
            return properties.failOpen();
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
