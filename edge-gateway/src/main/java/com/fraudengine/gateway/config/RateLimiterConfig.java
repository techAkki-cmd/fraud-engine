package com.fraudengine.gateway.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Bean
    RedisRateLimiter paymentRedisRateLimiter(
            @Value("${fraud-engine.rate-limit.replenish-rate:100}") int replenishRate,
            @Value("${fraud-engine.rate-limit.burst-capacity:200}") int burstCapacity,
            @Value("${fraud-engine.rate-limit.requested-tokens:1}") int requestedTokens) {
        return new RedisRateLimiter(replenishRate, burstCapacity, requestedTokens);
    }

    @Bean
    KeyResolver paymentRateLimitKeyResolver() {
        return exchange -> Mono.fromSupplier(() -> {
            String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
            if (StringUtils.hasText(apiKey)) {
                return "api-key:" + sha256(apiKey.trim());
            }

            if (exchange.getRequest().getRemoteAddress() == null) {
                return "ip:unknown";
            }
            return "ip:" + exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        });
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }
}
