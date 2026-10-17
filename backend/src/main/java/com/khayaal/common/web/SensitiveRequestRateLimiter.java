package com.khayaal.common.web;

@FunctionalInterface
public interface SensitiveRequestRateLimiter {
    boolean allow(String discriminator);
}
