package com.fraudengine.ai.payment;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fraudengine.ai.support.ContractValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventDecoder {

    static final String EXPECTED_SCHEMA_VERSION = "2";
    static final String EXPECTED_EVENT_TYPE = "PAYMENT_INGESTED";

    private final ObjectReader paymentReader;
    private final Validator validator;

    public PaymentEventDecoder(ObjectMapper objectMapper, Validator validator) {
        this.paymentReader = objectMapper.readerFor(PaymentPayload.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.validator = validator;
    }

    public DecodedPayment decode(ConsumerRecord<String, String> record) {
        requireHeader(record, "schema-version", EXPECTED_SCHEMA_VERSION);
        requireHeader(record, "event-type", EXPECTED_EVENT_TYPE);
        String correlationId = requiredHeader(record, "correlation-id");
        if (correlationId.length() > 128) {
            throw new ContractValidationException("correlation-id must not exceed 128 characters");
        }

        PaymentPayload payment = readPayment(record.value());
        validate(payment);
        if (record.key() == null || !record.key().equals(payment.paymentId().toString())) {
            throw new ContractValidationException("Kafka record key must equal paymentId");
        }
        return new DecodedPayment(payment, correlationId);
    }

    private PaymentPayload readPayment(String value) {
        if (value == null || value.isBlank()) {
            throw new ContractValidationException("Payment event value must not be empty");
        }
        try {
            return paymentReader.readValue(value);
        } catch (JsonProcessingException exception) {
            throw new ContractValidationException("Payment event is not valid schema-v2 JSON", exception);
        }
    }

    private void validate(PaymentPayload payment) {
        Set<ConstraintViolation<PaymentPayload>> violations = validator.validate(payment);
        if (violations.isEmpty()) {
            return;
        }
        String details = violations.stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(violation -> "%s %s".formatted(violation.getPropertyPath(), violation.getMessage()))
                .collect(Collectors.joining("; "));
        throw new ContractValidationException("Payment event validation failed: " + details);
    }

    private static void requireHeader(
            ConsumerRecord<String, String> record,
            String name,
            String expectedValue) {
        String actual = requiredHeader(record, name);
        if (!expectedValue.equals(actual)) {
            throw new ContractValidationException(
                    "%s must be '%s' but was '%s'".formatted(name, expectedValue, actual));
        }
    }

    private static String requiredHeader(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            throw new ContractValidationException("Missing required Kafka header: " + name);
        }
        String value = new String(header.value(), StandardCharsets.UTF_8);
        if (value.isBlank()) {
            throw new ContractValidationException("Kafka header must not be blank: " + name);
        }
        return value;
    }
}
