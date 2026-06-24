package com.fraudengine.ai.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fraudengine.ai.decision.FraudDecision;
import com.fraudengine.ai.payment.PaymentMethod;
import com.fraudengine.ai.payment.PaymentPayload;
import com.fraudengine.ai.payment.PaymentPayload.DeviceFingerprintRisk;
import org.junit.jupiter.api.Test;

class RiskScoringEngineTests {

    private final RiskScoringEngine engine = new RiskScoringEngine();

    @Test
    void lowRiskEnrichedPaymentIsSafeAndMitigationsClampToZero() {
        RiskProfile profile = engine.score(payment(
                "45.00",
                PaymentMethod.CARD,
                "merchant-neighborhood-market",
                new PaymentPayload.RiskContext(
                        new BigDecimal("1.05"),
                        1,
                        new BigDecimal("12"),
                        720,
                        false,
                        DeviceFingerprintRisk.LOW,
                        true)));

        assertThat(profile.decision()).isEqualTo(FraudDecision.SAFE);
        assertThat(profile.totalScore()).isZero();
        assertThat(profile.isFraud()).isFalse();
    }

    @Test
    void mediumRiskScoreRoutesToReview() {
        RiskProfile profile = engine.score(payment(
                "875.40",
                PaymentMethod.CARD,
                "merchant-marketplace-seller",
                new PaymentPayload.RiskContext(
                        new BigDecimal("6.10"),
                        2,
                        new BigDecimal("1325"),
                        92,
                        false,
                        DeviceFingerprintRisk.MEDIUM,
                        false)));

        assertThat(profile.decision()).isEqualTo(FraudDecision.REVIEW);
        assertThat(profile.totalScore()).isEqualTo(55);
    }

    @Test
    void highRiskScoreRoutesToBlockAndClampsAtOneHundred() {
        RiskProfile profile = engine.score(payment(
                "9500.00",
                PaymentMethod.DIGITAL_WALLET,
                "merchant-mule-wallet-payout",
                new PaymentPayload.RiskContext(
                        new BigDecimal("7.25"),
                        8,
                        new BigDecimal("3480"),
                        1,
                        true,
                        DeviceFingerprintRisk.HIGH,
                        false)));

        assertThat(profile.decision()).isEqualTo(FraudDecision.BLOCK);
        assertThat(profile.totalScore()).isEqualTo(100);
        assertThat(profile.isFraud()).isTrue();
    }

    @Test
    void schemaV2FallbackRulesStillWork() {
        RiskProfile profile = engine.score(payment(
                "9500.00",
                PaymentMethod.DIGITAL_WALLET,
                "merchant-mule-wallet-payout",
                null));

        assertThat(profile.decision()).isEqualTo(FraudDecision.BLOCK);
        assertThat(profile.totalScore()).isEqualTo(95);
        assertThat(profile.triggeredRules()).hasSize(3);
    }

    private static PaymentPayload payment(
            String amount,
            PaymentMethod method,
            String merchantId,
            PaymentPayload.RiskContext riskContext) {
        return new PaymentPayload(
                UUID.randomUUID(),
                "acct-source",
                "acct-destination",
                merchantId,
                new BigDecimal(amount),
                "USD",
                method,
                Instant.parse("2024-01-01T00:00:00Z"),
                riskContext);
    }
}
