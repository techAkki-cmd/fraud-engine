package com.fraudengine.ai.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fraudengine.ai.decision.FraudDecision;
import com.fraudengine.ai.decision.FraudEvaluationResult;
import com.fraudengine.ai.payment.DecodedPayment;
import com.fraudengine.ai.support.RetryableFraudEvaluationException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class FraudDecisionPublisher {

    private static final int MAX_REASON_HEADER_BYTES = 512;
    private static final int MAX_VECTOR_HEADER_BYTES = 1024;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Duration publishTimeout;

    public FraudDecisionPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.ai.publish-timeout:10s}") Duration publishTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.publishTimeout = publishTimeout;
    }

    public void publish(ConsumerRecord<String, String> sourceRecord, DecodedPayment decodedPayment, FraudEvaluationResult result) {
        String targetTopic = switch (result.decision()) {
            case SAFE -> PaymentTopics.PAYMENT_CLEARED;
            case FRAUD -> PaymentTopics.PAYMENT_BLOCKED;
        };

        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(
                targetTopic,
                null,
                decodedPayment.payment().paymentId().toString(),
                sourceRecord.value());

        copyHeaderIfPresent(sourceRecord, producerRecord, "correlation-id");
        copyHeaderIfPresent(sourceRecord, producerRecord, "schema-version");
        copyHeaderIfPresent(sourceRecord, producerRecord, "event-type");
        copyHeaderIfPresent(sourceRecord, producerRecord, "ingested-at");
        addHeader(producerRecord, "fraud-decision", result.decision().name());
        addHeader(producerRecord, "fraud-reason", bounded(result.reasoning(), MAX_REASON_HEADER_BYTES));
        addHeader(producerRecord, "fraud-model", result.model());
        addHeader(producerRecord, "fraud-evaluated-at", DateTimeFormatter.ISO_INSTANT.format(result.evaluatedAt()));
        addHeader(producerRecord, "fraud-vector-matches", bounded(result.vectorMatches(), MAX_VECTOR_HEADER_BYTES));

        try {
            kafkaTemplate.send(producerRecord).get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RetryableFraudEvaluationException("Interrupted while publishing fraud decision", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new RetryableFraudEvaluationException("Failed to publish fraud decision to " + targetTopic, exception);
        }
    }

    private static void copyHeaderIfPresent(
            ConsumerRecord<String, String> sourceRecord,
            ProducerRecord<String, String> producerRecord,
            String headerName) {
        Header header = sourceRecord.headers().lastHeader(headerName);
        if (header != null) {
            producerRecord.headers().add(headerName, header.value());
        }
    }

    private static void addHeader(ProducerRecord<String, String> record, String headerName, String value) {
        record.headers().add(headerName, value.getBytes(StandardCharsets.UTF_8));
    }

    private static String bounded(String value, int maxBytes) {
        if (value == null || value.isBlank()) {
            return "";
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        int byteCount = 0;
        for (int i = 0; i < value.length(); i++) {
            String character = value.substring(i, i + 1);
            int next = character.getBytes(StandardCharsets.UTF_8).length;
            if (byteCount + next > maxBytes - 3) {
                break;
            }
            builder.append(character);
            byteCount += next;
        }
        return builder.append("...").toString();
    }
}
