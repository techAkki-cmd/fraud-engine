package com.fraudengine.ledger.messaging;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fraudengine.ledger.exception.ContractValidationException;
import jakarta.validation.Validation;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentEventDecoderTests {

    private final PaymentEventDecoder decoder = new PaymentEventDecoder(
            new ObjectMapper().registerModule(new JavaTimeModule()),
            Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void decodesAValidSchemaVersionTwoEvent() {
        UUID paymentId = UUID.randomUUID();
        ConsumerRecord<String, String> record = record(paymentId, "2", validJson(paymentId));

        DecodedPayment decoded = decoder.decode(record);

        assertThat(decoded.payment().paymentId()).isEqualTo(paymentId);
        assertThat(decoded.payment().destinationAccountId()).isEqualTo("account-2");
        assertThat(decoded.correlationId()).isEqualTo("correlation-1");
    }

    @Test
    void decodesSchemaVersionThreeEventWithRiskContextButLedgerIgnoresIt() {
        UUID paymentId = UUID.randomUUID();
        ConsumerRecord<String, String> record = record(paymentId, "3", validJsonWithRiskContext(paymentId));

        DecodedPayment decoded = decoder.decode(record);

        assertThat(decoded.payment().paymentId()).isEqualTo(paymentId);
        assertThat(decoded.payment().riskContext()).isNotNull();
        assertThat(decoded.payment().riskContext().merchantEstablishedLowRisk()).isTrue();
    }

    @Test
    void rejectsLegacySchemaVersionOneEvents() {
        UUID paymentId = UUID.randomUUID();

        assertThatThrownBy(() -> decoder.decode(record(paymentId, "1", validJson(paymentId))))
                .isInstanceOf(ContractValidationException.class)
                .hasMessageContaining("schema-version");
    }

    @Test
    void rejectsRecordKeysThatDoNotMatchThePaymentId() {
        UUID paymentId = UUID.randomUUID();
        ConsumerRecord<String, String> record = record(paymentId, "2", validJson(paymentId));
        ConsumerRecord<String, String> wrongKeyRecord = new ConsumerRecord<>(
                record.topic(),
                record.partition(),
                record.offset(),
                UUID.randomUUID().toString(),
                record.value());
        record.headers().forEach(header -> wrongKeyRecord.headers().add(header));

        assertThatThrownBy(() -> decoder.decode(wrongKeyRecord))
                .isInstanceOf(ContractValidationException.class)
                .hasMessageContaining("record key");
    }

    private static ConsumerRecord<String, String> record(
            UUID paymentId,
            String schemaVersion,
            String value) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                PaymentTopics.PAYMENT_INGEST,
                0,
                1L,
                paymentId.toString(),
                value);
        record.headers().add("schema-version", bytes(schemaVersion));
        record.headers().add("event-type", bytes("PAYMENT_INGESTED"));
        record.headers().add("correlation-id", bytes("correlation-1"));
        return record;
    }

    private static String validJson(UUID paymentId) {
        return """
                {
                  "paymentId": "%s",
                  "accountId": "account-1",
                  "destinationAccountId": "account-2",
                  "merchantId": "merchant-1",
                  "amount": %s,
                  "currency": "USD",
                  "paymentMethod": "CARD",
                  "occurredAt": "%s"
                }
                """.formatted(
                paymentId,
                new BigDecimal("25.50").toPlainString(),
                Instant.now().minusSeconds(1));
    }

    private static String validJsonWithRiskContext(UUID paymentId) {
        return """
                {
                  "paymentId": "%s",
                  "accountId": "account-1",
                  "destinationAccountId": "account-2",
                  "merchantId": "merchant-1",
                  "amount": %s,
                  "currency": "USD",
                  "paymentMethod": "CARD",
                  "occurredAt": "%s",
                  "riskContext": {
                    "amountToMedianRatio": 1.05,
                    "paymentAttemptsLast10Minutes": 1,
                    "ipGeoDistanceKmFromUsual": 12,
                    "destinationAccountAgeDays": 720,
                    "destinationPreviouslyFlagged": false,
                    "deviceFingerprintRisk": "LOW",
                    "merchantEstablishedLowRisk": true
                  }
                }
                """.formatted(
                paymentId,
                new BigDecimal("25.50").toPlainString(),
                Instant.now().minusSeconds(1));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
