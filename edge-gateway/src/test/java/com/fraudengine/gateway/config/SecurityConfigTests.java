package com.fraudengine.gateway.config;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTests {

    private final ReactiveJwtDecoder jwtDecoder = token -> {
        if (!"valid-token".equals(token)) {
            return Mono.error(new InvalidBearerTokenException("invalid token"));
        }
        return Mono.just(new Jwt(
                token,
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of("sub", "phase-8-test")));
    };

    private final SecurityWebFilterChain security = new SecurityConfig()
            .springSecurityFilterChain(
                    org.springframework.security.config.web.server.ServerHttpSecurity.http(),
                    jwtDecoder);

    @Test
    void paymentIngestionRequiresAuthentication() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/v1/payments"));

        StepVerifier.create(filter(exchange))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidJwtPaymentIngestionIsUnauthorized() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/v1/payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"));

        StepVerifier.create(filter(exchange))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validJwtPaymentIngestionPassesThrough() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/v1/payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"));

        StepVerifier.create(filter(exchange))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void corsPreflightIsPublic() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.options("/api/v1/payments")
                .header(HttpHeaders.ORIGIN, "http://localhost:3001")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,x-correlation-id"));

        StepVerifier.create(filter(exchange))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void paymentStreamIsPublic() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/stream/payments"));

        StepVerifier.create(filter(exchange))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void healthIsPublic() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/actuator/health"));

        StepVerifier.create(filter(exchange))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void unmatchedRoutesAreDenied() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/internal/v1/payments"));

        StepVerifier.create(filter(exchange))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private Mono<Void> filter(MockServerWebExchange exchange) {
        return security.getWebFilters()
                .collectList()
                .flatMap(filters -> invoke(filters, 0, exchange));
    }

    private Mono<Void> invoke(List<WebFilter> filters, int index, MockServerWebExchange exchange) {
        if (index == filters.size()) {
            return terminalChain().filter(exchange);
        }
        return filters.get(index).filter(exchange, next -> invoke(filters, index + 1, exchange));
    }

    private static MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request) {
        return MockServerWebExchange.from(request);
    }

    private static WebFilterChain terminalChain() {
        return exchange -> {
            exchange.getResponse().setStatusCode(HttpStatus.ACCEPTED);
            return Mono.empty();
        };
    }
}
