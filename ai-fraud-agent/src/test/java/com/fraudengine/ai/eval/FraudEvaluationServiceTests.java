package com.fraudengine.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.ai.decision.FraudDecision;
import com.fraudengine.ai.payment.PaymentMethod;
import com.fraudengine.ai.payment.PaymentPayload;
import com.fraudengine.ai.risk.RiskScoringEngine;
import org.springframework.ai.document.Document;
import org.junit.jupiter.api.Test;

class FraudEvaluationServiceTests {

    private final PaymentPayload payment = new PaymentPayload(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "acct-source",
            "acct-destination",
            "merchant-42",
            new BigDecimal("125.25"),
            "USD",
            PaymentMethod.CARD,
            Instant.parse("2024-01-01T00:00:00Z"),
            null);

    @Test
    void evaluatesStrictSafeDecisionWithVectorContext() {
        CapturingFraudAiClient aiClient = new CapturingFraudAiClient("""
                {"reasoning":"Payment resembles known recurring behavior."}
                """);
        FraudEvaluationService service = service(vectorClient(List.of(
                new Document("Normal recurring merchant payment", Map.of("expected_decision", "SAFE")))), aiClient, 512);

        var result = service.evaluate(payment);

        assertThat(result.decision()).isEqualTo(FraudDecision.SAFE);
        assertThat(result.riskScore()).isZero();
        assertThat(result.reasoning()).isEqualTo("Payment resembles known recurring behavior.");
        assertThat(result.model()).isEqualTo("test-model");
        assertThat(result.vectorMatches()).contains("Normal recurring merchant payment");
        assertThat(aiClient.userPrompt).contains("paymentId=11111111-1111-1111-1111-111111111111");
        assertThat(aiClient.userPrompt).contains("decision=SAFE");
        assertThat(aiClient.systemPrompt).contains("Return only strict JSON");
    }

    @Test
    void llmCannotOverrideRulesDecision() {
        PaymentPayload highRiskPayment = new PaymentPayload(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "acct-source",
                "acct-destination",
                "merchant-mule-wallet-payout",
                new BigDecimal("9500.00"),
                "USD",
                PaymentMethod.DIGITAL_WALLET,
                Instant.parse("2024-01-01T00:00:00Z"),
                null);
        FraudEvaluationService service = service(vectorClient(List.of()), new CapturingFraudAiClient("""
                {"reasoning":"The deterministic rules produced a block-level score."}
                """), 512);

        var result = service.evaluate(highRiskPayment);

        assertThat(result.decision()).isEqualTo(FraudDecision.BLOCK);
        assertThat(result.riskScore()).isEqualTo(95);
    }

    @Test
    void fallsBackWhenModelReturnsMalformedJson() {
        FraudEvaluationService service = service(vectorClient(List.of()), new CapturingFraudAiClient("not json"), 512);

        var result = service.evaluate(payment);

        assertThat(result.decision()).isEqualTo(FraudDecision.SAFE);
        assertThat(result.reasoning()).contains("AI explanation is temporarily unavailable");
    }

    @Test
    void fallsBackWhenModelReturnsUnknownFields() {
        FraudEvaluationService service = service(vectorClient(List.of()), new CapturingFraudAiClient("""
                {"reasoning":"ok","decision":"SAFE"}
                """), 512);

        var result = service.evaluate(payment);

        assertThat(result.decision()).isEqualTo(FraudDecision.SAFE);
        assertThat(result.reasoning()).contains("AI explanation is temporarily unavailable");
    }

    @Test
    void fallsBackWhenModelReasoningIsOversized() {
        FraudEvaluationService service = service(vectorClient(List.of()), new CapturingFraudAiClient("""
                {"reasoning":"This reasoning is too long"}
                """), 10);

        var result = service.evaluate(payment);

        assertThat(result.decision()).isEqualTo(FraudDecision.SAFE);
        assertThat(result.reasoning()).contains("AI explanation is temporarily unavailable");
    }

    @Test
    void fallsBackWhenVectorStoreFails() {
        FraudEvaluationService service = service(new VectorSearchClient() {
            @Override
            public List<Document> similarFraudProfiles(String query, int topK) {
                throw new IllegalStateException("pgvector down");
            }

            @Override
            public void add(List<Document> documents) {
            }
        }, new CapturingFraudAiClient("{}"), 512);

        var result = service.evaluate(payment);

        assertThat(result.decision()).isEqualTo(FraudDecision.SAFE);
        assertThat(result.vectorMatches()).isBlank();
        assertThat(result.reasoning()).contains("AI explanation is temporarily unavailable");
    }

    private FraudEvaluationService service(VectorSearchClient vectorClient, FraudAiClient aiClient, int maxReasoningLength) {
        return new FraudEvaluationService(
                new RiskScoringEngine(),
                vectorClient,
                aiClient,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2024-01-01T00:00:02Z"), ZoneOffset.UTC),
                "test-model",
                true,
                3,
                maxReasoningLength,
                Duration.ofSeconds(20));
    }

    private static VectorSearchClient vectorClient(List<Document> documents) {
        return new VectorSearchClient() {
            @Override
            public List<Document> similarFraudProfiles(String query, int topK) {
                return documents.stream().limit(topK).toList();
            }

            @Override
            public void add(List<Document> documents) {
            }
        };
    }

    private static final class CapturingFraudAiClient implements FraudAiClient {
        private final String response;
        private String systemPrompt;
        private String userPrompt;

        private CapturingFraudAiClient(String response) {
            this.response = response;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            return response;
        }
    }
}
