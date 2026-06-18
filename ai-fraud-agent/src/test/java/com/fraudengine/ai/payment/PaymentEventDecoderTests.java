package com.fraudengine.ai.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fraudengine.ai.support.ContractValidationException;
import jakarta.validation.Validation;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class PaymentEventDecoderTests {

    private final UUID paymentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final PaymentEventDecoder decoder = new PaymentEventDecoder(
            new ObjectMapper().registerModule(new JavaTimeModule()),
            Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void decodesValidSchemaV2PaymentAndCorrelationId() {
        ConsumerRecord<String, String> record = validRecord();

        DecodedPayment decoded = decoder.decode(record);

        assertThat(decoded.correlationId()).isEqualTo("corr-123");
        assertThat(decoded.payment().paymentId()).isEqualTo(paymentId);
        assertThat(decoded.payment().destinationAccountId()).isEqualTo("acct-destination");
    }

    @Test
    void rejectsSchemaV1BeforeParsingPayload() {
        ConsumerRecord<String, String> record = validRecord();
        record.headers().remove("schema-version");
        record.headers().add("schema-version", bytes("1"));

        assertThatThrownBy(() -> decoder.decode(record))
                .isInstanceOf(ContractValidationException.class)
                .hasMessageContaining("schema-version");
    }

    @Test
    void rejectsUnknownJsonFields() {
        ConsumerRecord<String, String> record = validRecord("""
                {
                  "paymentId":"11111111-1111-1111-1111-111111111111",
                  "accountId":"acct-source",
                  "destinationAccountId":"acct-destination",
                  "merchantId":"merchant-42",
                  "amount": "125.25",
                  "currency": "USD",
                  "paymentMethod": "CARD",
                  "occurredAt": "2024-01-01T00:00:00Z",
                  "extra": "not allowed"
                }
                """);

        assertThatThrownBy(() -> decoder.decode(record))
                .isInstanceOf(ContractValidationException.class)
                .hasMessageContaining("schema-v2 JSON");
    }

    @Test
    void rejectsKafkaKeyMismatch() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("payment-ingest", 0, 0L, UUID.randomUUID().toString(), validJson());
        addRequiredHeaders(record);

        assertThatThrownBy(() -> decoder.decode(record))
                .isInstanceOf(ContractValidationException.class)
                .hasMessageContaining("record key");
    }

    @Test
    void rejectsInvalidAmountScale() {
        ConsumerRecord<String, String> record = validRecord(validJson().replace("\"125.25\"", "\"125.255\""));

        assertThatThrownBy(() -> decoder.decode(record))
                .isInstanceOf(ContractValidationException.class)
                .hasMessageContaining("amount");
    }

    private ConsumerRecord<String, String> validRecord() {
        return validRecord(validJson());
    }

    private ConsumerRecord<String, String> validRecord(String json) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("payment-ingest", 0, 0L, paymentId.toString(), json);
        addRequiredHeaders(record);
        return record;
    }

    private void addRequiredHeaders(ConsumerRecord<String, String> record) {
        record.headers().add("schema-version", bytes("2"));
        record.headers().add("event-type", bytes("PAYMENT_INGESTED"));
        record.headers().add("correlation-id", bytes("corr-123"));
        record.headers().add("ingested-at", bytes(Instant.parse("2024-01-01T00:00:01Z").toString()));
    }

    private String validJson() {
        return """
                {
                  "paymentId":"11111111-1111-1111-1111-111111111111",
                  "accountId":"acct-source",
                  "destinationAccountId":"acct-destination",
                  "merchantId":"merchant-42",
                  "amount": "125.25",
                  "currency": "USD",
                  "paymentMethod": "CARD",
                  "occurredAt": "2024-01-01T00:00:00Z"
                }
                """;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
