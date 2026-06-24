package com.fraudengine.ledger.messaging;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fraudengine.ledger.exception.ContractValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventDecoder {

    static final Set<String> SUPPORTED_SCHEMA_VERSIONS = Set.of("2", "3");
    static final String EXPECTED_EVENT_TYPE = "PAYMENT_INGESTED";

    private final ObjectReader paymentReader;
    private final Validator validator;

    public PaymentEventDecoder(ObjectMapper objectMapper, Validator validator) {
        this.paymentReader = objectMapper.readerFor(PaymentEvent.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.validator = validator;
    }

    public DecodedPayment decode(ConsumerRecord<String, String> record) {
        requireOneOfHeader(record, "schema-version", SUPPORTED_SCHEMA_VERSIONS);
        requireHeader(record, "event-type", EXPECTED_EVENT_TYPE);
        String correlationId = requiredHeader(record, "correlation-id");
        if (correlationId.length() > 128) {
            throw new ContractValidationException("correlation-id must not exceed 128 characters");
        }

        PaymentEvent payment = readPayment(record.value());
        validate(payment);

        if (record.key() == null || !record.key().equals(payment.paymentId().toString())) {
            throw new ContractValidationException("Kafka record key must equal paymentId");
        }
        return new DecodedPayment(payment, correlationId);
    }

    private PaymentEvent readPayment(String value) {
        if (value == null || value.isBlank()) {
            throw new ContractValidationException("Payment event value must not be empty");
        }
        try {
            return paymentReader.readValue(value);
        } catch (JsonProcessingException exception) {
            throw new ContractValidationException("Payment event is not valid schema-v2/v3 JSON", exception);
        }
    }

    private void validate(PaymentEvent payment) {
        Set<ConstraintViolation<PaymentEvent>> violations = validator.validate(payment);
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
        String actualValue = requiredHeader(record, name);
        if (!expectedValue.equals(actualValue)) {
            throw new ContractValidationException(
                    "%s must be '%s' but was '%s'".formatted(name, expectedValue, actualValue));
        }
    }

    private static void requireOneOfHeader(
            ConsumerRecord<String, String> record,
            String name,
            Set<String> expectedValues) {
        String actualValue = requiredHeader(record, name);
        if (!expectedValues.contains(actualValue)) {
            throw new ContractValidationException(
                    "%s must be one of %s but was '%s'".formatted(name, expectedValues, actualValue));
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
