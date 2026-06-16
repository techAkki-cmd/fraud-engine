package com.fraudengine.gateway.api;

import java.time.Duration;
import java.util.UUID;

import com.fraudengine.gateway.messaging.PaymentEventPublisher;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@RestController
public class PaymentIngestionController {

    static final String PAYMENT_ROUTE_ID = "payment-ingestion-route";
    private static final String QUEUED = "QUEUED";

    private final PaymentEventPublisher publisher;
    private final Duration publishTimeout;

    public PaymentIngestionController(
            PaymentEventPublisher publisher,
            @Value("${fraud-engine.kafka.publish-timeout:12s}") Duration publishTimeout) {
        this.publisher = publisher;
        this.publishTimeout = publishTimeout;
    }

    @PostMapping("/internal/v1/payments")
    Mono<ResponseEntity<PaymentAcceptedResponse>> ingest(
            @Valid @RequestBody PaymentRequest payment,
            @RequestHeader(name = "X-Correlation-ID", required = false) String requestedCorrelationId,
            ServerWebExchange exchange) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        if (route == null || !PAYMENT_ROUTE_ID.equals(route.getId())) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
        }

        String correlationId = StringUtils.hasText(requestedCorrelationId)
                ? requestedCorrelationId.trim()
                : UUID.randomUUID().toString();
        if (correlationId.length() > 128) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "X-Correlation-ID must not exceed 128 characters"));
        }

        return publisher.publish(payment, correlationId)
                .timeout(publishTimeout)
                .map(receipt -> ResponseEntity.accepted().body(
                        new PaymentAcceptedResponse(payment.paymentId(), correlationId, QUEUED)))
                .onErrorMap(
                        exception -> !(exception instanceof ResponseStatusException),
                        exception -> new ResponseStatusException(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "Payment could not be acknowledged by Kafka",
                                exception));
    }
}
