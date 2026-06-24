package com.fraudengine.gateway.api;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.fraudengine.gateway.messaging.PaymentEventPublisher;
import com.fraudengine.gateway.messaging.PublishReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

class PaymentIngestionControllerTests {

    private PaymentEventPublisher publisher;
    private PaymentIngestionController controller;
    private ServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        publisher = mock(PaymentEventPublisher.class);
        controller = new PaymentIngestionController(publisher, Duration.ofSeconds(1));
        exchange = mock(ServerWebExchange.class);

        Route route = mock(Route.class);
        when(route.getId()).thenReturn(PaymentIngestionController.PAYMENT_ROUTE_ID);
        when(exchange.getAttribute(GATEWAY_ROUTE_ATTR)).thenReturn(route);
    }

    @Test
    void returnsAcceptedOnlyAfterPublisherCompletes() {
        PaymentRequest payment = validPayment();
        when(publisher.publish(payment, "correlation-1"))
                .thenReturn(Mono.just(new PublishReceipt("payment-ingest", 2, 42)));

        StepVerifier.create(controller.ingest(payment, "correlation-1", exchange))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().paymentId()).isEqualTo(payment.paymentId());
                    assertThat(response.getBody().correlationId()).isEqualTo("correlation-1");
                    assertThat(response.getBody().status()).isEqualTo("QUEUED");
                })
                .verifyComplete();
    }

    @Test
    void mapsPublisherFailureToServiceUnavailable() {
        when(publisher.publish(any(), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("broker unavailable")));

        StepVerifier.create(controller.ingest(validPayment(), "correlation-2", exchange))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                })
                .verify();
    }

    @Test
    void rejectsDirectCallsToInternalEndpoint() {
        when(exchange.getAttribute(GATEWAY_ROUTE_ATTR)).thenReturn(null);

        StepVerifier.create(controller.ingest(validPayment(), null, exchange))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND);
                })
                .verify();
    }

    private static PaymentRequest validPayment() {
        return new PaymentRequest(
                UUID.randomUUID(),
                "account-1",
                "account-2",
                "merchant-1",
                new BigDecimal("125.50"),
                "USD",
                PaymentMethod.CARD,
                Instant.now().minusSeconds(1),
                null);
    }
}
