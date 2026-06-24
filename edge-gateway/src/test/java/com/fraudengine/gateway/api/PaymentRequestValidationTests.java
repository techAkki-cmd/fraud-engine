package com.fraudengine.gateway.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsCanonicalPayment() {
        PaymentRequest request = validPayment();

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsInvalidAmountCurrencyAndFutureTimestamp() {
        PaymentRequest request = new PaymentRequest(
                UUID.randomUUID(),
                "account-1",
                "account-2",
                "merchant-1",
                new BigDecimal("0.001"),
                "usd",
                PaymentMethod.CARD,
                Instant.now().plusSeconds(60),
                null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("amount", "currency", "occurredAt");
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
