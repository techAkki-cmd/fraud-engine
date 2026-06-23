package com.fraudengine.ai.risk;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fraudengine.ai.decision.FraudDecision;
import com.fraudengine.ai.payment.PaymentMethod;
import com.fraudengine.ai.payment.PaymentPayload;
import com.fraudengine.ai.payment.PaymentPayload.DeviceFingerprintRisk;
import org.springframework.stereotype.Service;

@Service
public class RiskScoringEngine {

    private static final BigDecimal FIVE = new BigDecimal("5.0");
    private static final BigDecimal FIVE_THOUSAND = new BigDecimal("5000.00");
    private static final BigDecimal THOUSAND_KM = new BigDecimal("1000.0");

    public RiskProfile score(PaymentPayload payment) {
        ScoreAccumulator score = new ScoreAccumulator();
        PaymentPayload.RiskContext riskContext = payment.riskContext();

        if (riskContext == null) {
            applyBackwardCompatibleFallbackRules(payment, score);
        } else {
            applyEnrichedRules(payment, riskContext, score);
        }

        int clamped = Math.max(0, Math.min(100, score.value));
        return new RiskProfile(clamped, decisionFor(clamped), score.rules);
    }

    private static void applyEnrichedRules(
            PaymentPayload payment,
            PaymentPayload.RiskContext riskContext,
            ScoreAccumulator score) {
        if (riskContext.amountToMedianRatio() != null
                && riskContext.amountToMedianRatio().compareTo(FIVE) > 0) {
            score.add(30, "Amount is greater than 5x the user's historical median");
        }
        if (riskContext.paymentAttemptsLast10Minutes() != null
                && riskContext.paymentAttemptsLast10Minutes() > 5) {
            score.add(40, "More than 5 payment attempts in the last 10 minutes");
        }
        if (riskContext.ipGeoDistanceKmFromUsual() != null
                && riskContext.ipGeoDistanceKmFromUsual().compareTo(THOUSAND_KM) > 0) {
            score.add(25, "IP geographic distance is greater than 1000km from usual location");
        }
        if (payment.paymentMethod() == PaymentMethod.DIGITAL_WALLET
                && riskContext.destinationAccountAgeDays() != null
                && riskContext.destinationAccountAgeDays() < 3) {
            score.add(35, "Digital wallet payment to destination account younger than 3 days");
        }
        if (Boolean.TRUE.equals(riskContext.destinationPreviouslyFlagged())) {
            score.add(50, "Destination account was previously flagged in suspicious graphs");
        }
        if (riskContext.deviceFingerprintRisk() == DeviceFingerprintRisk.HIGH) {
            score.add(30, "Device fingerprint risk is HIGH");
        }
        if (riskContext.destinationAccountAgeDays() != null
                && riskContext.destinationAccountAgeDays() > 365) {
            score.add(-20, "Destination account age is greater than 365 days");
        }
        if (Boolean.TRUE.equals(riskContext.merchantEstablishedLowRisk())) {
            score.add(-15, "Merchant has an established low-risk history");
        }
    }

    private static void applyBackwardCompatibleFallbackRules(PaymentPayload payment, ScoreAccumulator score) {
        if (payment.paymentMethod() == PaymentMethod.DIGITAL_WALLET) {
            score.add(30, "Fallback: payment method is DIGITAL_WALLET");
        }
        if (payment.amount().compareTo(FIVE_THOUSAND) > 0) {
            score.add(40, "Fallback: amount is greater than 5000.00");
        }
        String merchant = payment.merchantId().toLowerCase(Locale.ROOT);
        if (merchant.contains("crypto") || merchant.contains("mule") || merchant.contains("hawala")) {
            score.add(25, "Fallback: merchant contains crypto/mule/hawala risk indicator");
        }
    }

    private static FraudDecision decisionFor(int score) {
        if (score < 40) {
            return FraudDecision.SAFE;
        }
        if (score < 75) {
            return FraudDecision.REVIEW;
        }
        return FraudDecision.BLOCK;
    }

    private static final class ScoreAccumulator {
        private int value;
        private final List<String> rules = new ArrayList<>();

        private void add(int points, String rule) {
            value += points;
            rules.add("%+d %s".formatted(points, rule));
        }
    }
}
