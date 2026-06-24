package com.fraudengine.ai.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

public record PaymentPayload(
        @NotNull UUID paymentId,
        @NotBlank @Size(max = 64) String accountId,
        @NotBlank @Size(max = 64) String destinationAccountId,
        @NotBlank @Size(max = 64) String merchantId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotNull PaymentMethod paymentMethod,
        @NotNull @PastOrPresent Instant occurredAt,
        @Valid RiskContext riskContext) {

    public enum DeviceFingerprintRisk {
        LOW,
        MEDIUM,
        HIGH
    }

    public record RiskContext(
            @DecimalMin("0.0") BigDecimal amountToMedianRatio,
            @Min(0) Integer paymentAttemptsLast10Minutes,
            @DecimalMin("0.0") BigDecimal ipGeoDistanceKmFromUsual,
            @Min(0) Integer destinationAccountAgeDays,
            Boolean destinationPreviouslyFlagged,
            DeviceFingerprintRisk deviceFingerprintRisk,
            Boolean merchantEstablishedLowRisk) {
    }
}
