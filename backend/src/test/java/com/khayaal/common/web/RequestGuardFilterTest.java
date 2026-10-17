package com.khayaal.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequestGuardFilterTest {
    @Test
    void suppliesCorrelationIdAndLimitsSensitiveWrites() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        SensitiveRequestRateLimiter rateLimiter = ignored -> requests.incrementAndGet() <= 20;
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new Endpoint())
                .addFilters(new RequestGuardFilter(rateLimiter))
                .build();

        mvc.perform(post("/api/auth/login").header("X-Request-ID", "test-request"))
                .andExpect(status().isOk()).andExpect(header().string("X-Request-ID", "test-request"));
        for (int request = 1; request < 20; request++)
            mvc.perform(post("/api/auth/login")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login")).andExpect(status().isTooManyRequests());
    }

    @RestController
    static class Endpoint {
        @PostMapping("/api/auth/login")
        void login() { }
    }
}
