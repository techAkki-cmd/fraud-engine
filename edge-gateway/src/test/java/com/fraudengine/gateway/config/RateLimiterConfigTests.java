package com.fraudengine.gateway.config;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterConfigTests {

    private final KeyResolver resolver = new RateLimiterConfig().paymentRateLimitKeyResolver();

    @Test
    void hashesApiKeysBeforeUsingThemAsRedisKeys() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/payments")
                        .header("X-API-Key", "secret-api-key")
                        .remoteAddress(new InetSocketAddress("203.0.113.10", 1234))
                        .build());

        StepVerifier.create(resolver.resolve(exchange))
                .assertNext(key -> {
                    assertThat(key).startsWith("api-key:");
                    assertThat(key).doesNotContain("secret-api-key");
                    assertThat(key).hasSize("api-key:".length() + 64);
                })
                .verifyComplete();
    }

    @Test
    void fallsBackToSocketRemoteAddress() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/payments")
                        .remoteAddress(new InetSocketAddress("203.0.113.10", 1234))
                        .build());

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("ip:203.0.113.10")
                .verifyComplete();
    }
}
