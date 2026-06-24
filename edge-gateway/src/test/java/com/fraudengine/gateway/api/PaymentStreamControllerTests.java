package com.fraudengine.gateway.api;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.gateway.messaging.PaymentTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.kafka.support.Acknowledgment;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentStreamControllerTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void emitsClearedPaymentAsSafeSseEventAndAcknowledgesOffset() {
        PaymentStreamController controller = controller();
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        ConsumerRecord<String, String> record = record(PaymentTopics.PAYMENT_CLEARED);

        StepVerifier.create(paymentEvents(controller).take(1))
                .then(() -> controller.consumeEvaluation(record, acknowledgment))
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("payment-evaluation");
                    assertThat(event.id()).isEqualTo("018f5f71-4a89-7e0e-b273-54a53df2b948");
                    assertThat(event.data()).contains("\"status\":\"SAFE\"");
                    assertThat(event.data()).contains("\"aiReasoning\":\"cleared by mock-ai\"");
                    assertThat(event.data()).contains("\"correlationId\":\"correlation-123\"");
                })
                .verifyComplete();

        verify(acknowledgment).acknowledge();
    }

    @Test
    void emitsBlockedPaymentAsBlockSseEventAndAcknowledgesOffset() {
        PaymentStreamController controller = controller();
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        ConsumerRecord<String, String> record = record(PaymentTopics.PAYMENT_BLOCKED);

        StepVerifier.create(paymentEvents(controller).take(1))
                .then(() -> controller.consumeEvaluation(record, acknowledgment))
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("payment-evaluation");
                    assertThat(event.data()).contains("\"status\":\"BLOCK\"");
                    assertThat(event.data()).contains("\"aiReasoning\":\"cleared by mock-ai\"");
                })
                .verifyComplete();

        verify(acknowledgment).acknowledge();
    }

    @Test
    void emitsReviewPaymentAsReviewSseEventAndAcknowledgesOffset() {
        PaymentStreamController controller = controller();
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        ConsumerRecord<String, String> record = record(PaymentTopics.PAYMENT_REVIEW);
        record.headers().add("risk-score", "55".getBytes(StandardCharsets.UTF_8));

        StepVerifier.create(paymentEvents(controller).take(1))
                .then(() -> controller.consumeEvaluation(record, acknowledgment))
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("payment-evaluation");
                    assertThat(event.data()).contains("\"status\":\"REVIEW\"");
                    assertThat(event.data()).contains("\"riskScore\":55");
                })
                .verifyComplete();

        verify(acknowledgment).acknowledge();
    }

    @Test
    void replaysKafkaEvaluationToLateSubscribers() {
        PaymentStreamController controller = controller();
        Acknowledgment acknowledgment = mock(Acknowledgment.class);

        controller.consumeEvaluation(record(PaymentTopics.PAYMENT_CLEARED), acknowledgment);

        StepVerifier.create(paymentEvents(controller).take(1))
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("payment-evaluation");
                    assertThat(event.id()).isEqualTo("018f5f71-4a89-7e0e-b273-54a53df2b948");
                    assertThat(event.data()).contains("\"status\":\"SAFE\"");
                })
                .verifyComplete();

        verify(acknowledgment).acknowledge();
    }

    @Test
    void multicastsOneKafkaEvaluationToMultipleSubscribers() throws InterruptedException {
        PaymentStreamController controller = controller();
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        CountDownLatch latch = new CountDownLatch(2);
        List<String> firstSubscriber = new ArrayList<>();
        List<String> secondSubscriber = new ArrayList<>();

        Disposable first = paymentEvents(controller)
                .take(1)
                .subscribe(event -> {
                    firstSubscriber.add(event.data());
                    latch.countDown();
                });
        Disposable second = paymentEvents(controller)
                .take(1)
                .subscribe(event -> {
                    secondSubscriber.add(event.data());
                    latch.countDown();
                });

        controller.consumeEvaluation(record(PaymentTopics.PAYMENT_BLOCKED), acknowledgment);

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(firstSubscriber).hasSize(1);
        assertThat(secondSubscriber).containsExactly(firstSubscriber.getFirst());
        verify(acknowledgment).acknowledge();

        first.dispose();
        second.dispose();
    }

    @Test
    void acknowledgesMalformedKafkaPayloadWithoutEmitting() {
        PaymentStreamController controller = controller();
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        ConsumerRecord<String, String> malformed = new ConsumerRecord<>(
                PaymentTopics.PAYMENT_CLEARED,
                0,
                42L,
                "payment-1",
                "{\"paymentId\":");

        controller.consumeEvaluation(malformed, acknowledgment);

        verify(acknowledgment).acknowledge();
    }

    @Test
    void emitsHeartbeatEvents() {
        PaymentStreamController controller = new PaymentStreamController(
                objectMapper,
                Sinks.many().replay().limit(8),
                Duration.ofSeconds(1));

        StepVerifier.withVirtualTime(() -> controller.streamPayments()
                        .filter(event -> "heartbeat".equals(event.event()))
                        .take(1))
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("heartbeat");
                    assertThat(event.data()).isEqualTo("{\"type\":\"heartbeat\"}");
                })
                .verifyComplete();
    }

    private PaymentStreamController controller() {
        return new PaymentStreamController(
                objectMapper,
                Sinks.many().replay().limit(8),
                Duration.ofHours(1));
    }

    private Flux<ServerSentEvent<String>> paymentEvents(PaymentStreamController controller) {
        return controller.streamPayments()
                .filter(event -> "payment-evaluation".equals(event.event()));
    }

    private ConsumerRecord<String, String> record(String topic) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                topic,
                0,
                42L,
                "018f5f71-4a89-7e0e-b273-54a53df2b948",
                """
                        {
                          "paymentId": "018f5f71-4a89-7e0e-b273-54a53df2b948",
                          "accountId": "acct-source-001",
                          "destinationAccountId": "acct-destination-001",
                          "merchantId": "merchant-001",
                          "amount": "25.00",
                          "currency": "USD",
                          "paymentMethod": "CARD",
                          "occurredAt": "2026-06-19T10:15:30Z"
                        }
                        """);
        record.headers().add("correlation-id", "correlation-123".getBytes(StandardCharsets.UTF_8));
        record.headers().add("fraud-reason", "cleared by mock-ai".getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
