package com.fraudengine.gateway.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.gateway.api.PaymentRequest;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ReactiveKafkaPaymentEventPublisher implements PaymentEventPublisher {

    private static final String SCHEMA_VERSION = "2";
    private static final TextMapSetter<ProducerRecord<String, String>> KAFKA_HEADER_SETTER =
            (record, key, value) -> {
                if (record != null && key != null && value != null) {
                    record.headers().remove(key);
                    record.headers().add(key, bytes(value));
                }
            };

    private final ReactiveKafkaProducerTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ReactiveKafkaPaymentEventPublisher(
            ReactiveKafkaProducerTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this(kafkaTemplate, objectMapper, Clock.systemUTC());
    }

    ReactiveKafkaPaymentEventPublisher(
            ReactiveKafkaProducerTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            Clock clock) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public Mono<PublishReceipt> publish(PaymentRequest payment, String correlationId) {
        return Mono.fromCallable(() -> createRecord(payment, correlationId))
                .flatMap(kafkaTemplate::send)
                .map(result -> new PublishReceipt(
                        result.recordMetadata().topic(),
                        result.recordMetadata().partition(),
                        result.recordMetadata().offset()));
    }

    private ProducerRecord<String, String> createRecord(
            PaymentRequest payment,
            String correlationId) throws JsonProcessingException {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                PaymentTopics.PAYMENT_INGEST,
                payment.paymentId().toString(),
                objectMapper.writeValueAsString(payment));
        record.headers().add("correlation-id", bytes(correlationId));
        record.headers().add("schema-version", bytes(SCHEMA_VERSION));
        record.headers().add("event-type", bytes("PAYMENT_INGESTED"));
        record.headers().add("ingested-at", bytes(Instant.now(clock).toString()));
        GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .inject(Context.current(), record, KAFKA_HEADER_SETTER);
        return record;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
