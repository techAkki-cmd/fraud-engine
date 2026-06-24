package com.fraudengine.ai.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fraudengine.ai.decision.FraudDecision;
import com.fraudengine.ai.decision.FraudEvaluationResult;
import com.fraudengine.ai.payment.DecodedPayment;
import com.fraudengine.ai.payment.PaymentMethod;
import com.fraudengine.ai.payment.PaymentPayload;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

class FraudDecisionPublisherTests {

    @Test
    void publishesSafePaymentToClearedWithOriginalAndFraudHeaders() {
        KafkaTemplate<String, String> kafkaTemplate = org.mockito.Mockito.mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        FraudDecisionPublisher publisher = new FraudDecisionPublisher(kafkaTemplate, Duration.ofSeconds(1));

        ConsumerRecord<String, String> sourceRecord = sourceRecord();
        PaymentPayload payment = payment();
        FraudEvaluationResult result = new FraudEvaluationResult(
                FraudDecision.SAFE,
                0,
                List.of(),
                "Low risk",
                "mock-ai",
                "score=n/a",
                Instant.parse("2024-01-01T00:00:02Z"));

        publisher.publish(sourceRecord, new DecodedPayment(payment, "corr-123"), result);

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();
        assertThat(record.topic()).isEqualTo(PaymentTopics.PAYMENT_CLEARED);
        assertThat(record.key()).isEqualTo(payment.paymentId().toString());
        assertThat(record.value()).isEqualTo(sourceRecord.value());
        assertThat(header(record, "correlation-id")).isEqualTo("corr-123");
        assertThat(header(record, "schema-version")).isEqualTo("2");
        assertThat(header(record, "event-type")).isEqualTo("PAYMENT_INGESTED");
        assertThat(header(record, "fraud-decision")).isEqualTo("SAFE");
        assertThat(header(record, "risk-score")).isEqualTo("0");
        assertThat(header(record, "fraud-reason")).isEqualTo("Low risk");
        assertThat(header(record, "fraud-model")).isEqualTo("mock-ai");
    }

    @Test
    void publishesReviewPaymentToReviewTopic() {
        KafkaTemplate<String, String> kafkaTemplate = org.mockito.Mockito.mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        FraudDecisionPublisher publisher = new FraudDecisionPublisher(kafkaTemplate, Duration.ofSeconds(1));

        publisher.publish(sourceRecord(), new DecodedPayment(payment(), "corr-123"), new FraudEvaluationResult(
                FraudDecision.REVIEW,
                55,
                List.of("+30 amount", "+25 geo distance"),
                "Requires step-up",
                "mock-ai",
                "moderate-risk",
                Instant.parse("2024-01-01T00:00:02Z")));

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo(PaymentTopics.PAYMENT_REVIEW);
        assertThat(header(captor.getValue(), "fraud-decision")).isEqualTo("REVIEW");
        assertThat(header(captor.getValue(), "risk-score")).isEqualTo("55");
        assertThat(header(captor.getValue(), "risk-rules")).contains("+30 amount");
    }

    @Test
    void publishesBlockedPaymentToBlocked() {
        KafkaTemplate<String, String> kafkaTemplate = org.mockito.Mockito.mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        FraudDecisionPublisher publisher = new FraudDecisionPublisher(kafkaTemplate, Duration.ofSeconds(1));

        publisher.publish(sourceRecord(), new DecodedPayment(payment(), "corr-123"), new FraudEvaluationResult(
                FraudDecision.BLOCK,
                90,
                List.of("+50 flagged destination"),
                "Digital wallet velocity",
                "mock-ai",
                "velocity",
                Instant.parse("2024-01-01T00:00:02Z")));

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo(PaymentTopics.PAYMENT_BLOCKED);
        assertThat(header(captor.getValue(), "fraud-decision")).isEqualTo("BLOCK");
    }

    private static ConsumerRecord<String, String> sourceRecord() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                PaymentTopics.PAYMENT_INGEST,
                0,
                10L,
                "11111111-1111-1111-1111-111111111111",
                "{\"paymentId\":\"11111111-1111-1111-1111-111111111111\"}");
        record.headers().add("correlation-id", bytes("corr-123"));
        record.headers().add("schema-version", bytes("2"));
        record.headers().add("event-type", bytes("PAYMENT_INGESTED"));
        record.headers().add("ingested-at", bytes("2024-01-01T00:00:01Z"));
        return record;
    }

    private static PaymentPayload payment() {
        return new PaymentPayload(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "acct-source",
                "acct-destination",
                "merchant-42",
                new java.math.BigDecimal("125.25"),
                "USD",
                PaymentMethod.CARD,
                Instant.parse("2024-01-01T00:00:00Z"),
                null);
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
