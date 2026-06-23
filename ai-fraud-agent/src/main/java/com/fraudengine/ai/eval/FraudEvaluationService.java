package com.fraudengine.ai.eval;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.ai.decision.FraudEvaluationResult;
import com.fraudengine.ai.payment.PaymentPayload;
import com.fraudengine.ai.risk.RiskProfile;
import com.fraudengine.ai.risk.RiskScoringEngine;
import com.fraudengine.ai.support.RetryableFraudEvaluationException;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class FraudEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(FraudEvaluationService.class);

    private static final String SYSTEM_PROMPT = """
            You are a senior financial risk analyst for a core banking payment integrity system.
            A deterministic Java rules engine has already made the final routing decision.
            Your job is explanation only: summarize why the fixed decision was made from the provided
            triggered rules and similar historical records.
            Return only strict JSON with this exact schema:
            {"reasoning":"short operational reason"}
            Do not include markdown, code fences, extra keys, comments, or prose outside JSON.
            Do not include or infer a decision field. Do not contradict the fixed rules-engine decision.
            """;

    private final RiskScoringEngine riskScoringEngine;
    private final VectorSearchClient vectorSearchClient;
    private final FraudAiClient fraudAiClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String modelName;
    private final boolean modelCallEnabled;
    private final int topK;
    private final int maxReasoningLength;

    public FraudEvaluationService(
            RiskScoringEngine riskScoringEngine,
            VectorSearchClient vectorSearchClient,
            FraudAiClient fraudAiClient,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${app.ai.provider-name}") String modelName,
            @Value("${app.ai.model-call-enabled:true}") boolean modelCallEnabled,
            @Value("${app.ai.vector-top-k:3}") int topK,
            @Value("${app.ai.max-reasoning-length:512}") int maxReasoningLength,
            @Value("${app.ai.decision-timeout:20s}") Duration decisionTimeout) {
        this.riskScoringEngine = riskScoringEngine;
        this.vectorSearchClient = vectorSearchClient;
        this.fraudAiClient = fraudAiClient;
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        this.clock = clock;
        this.modelName = modelName;
        this.modelCallEnabled = modelCallEnabled;
        this.topK = topK;
        this.maxReasoningLength = maxReasoningLength;
    }

    public FraudEvaluationResult evaluate(PaymentPayload payment) {
        RiskProfile riskProfile = riskScoringEngine.score(payment);
        String context = buildContext(payment);
        List<Document> similarDocuments = searchSimilarProfiles(context);
        String vectorSummary = summarize(similarDocuments);
        String userPrompt = buildUserPrompt(payment, riskProfile, context, vectorSummary);
        ParsedReasoning parsedReasoning = explainWithModel(payment, riskProfile, userPrompt);
        return new FraudEvaluationResult(
                riskProfile.decision(),
                riskProfile.totalScore(),
                riskProfile.triggeredRules(),
                parsedReasoning.reasoning(),
                modelName,
                vectorSummary,
                clock.instant());
    }

    public String buildContext(PaymentPayload payment) {
        return "Transaction of %s %s from account %s to account %s for merchant %s via %s at %s"
                .formatted(
                        payment.currency(),
                        payment.amount().toPlainString(),
                        payment.accountId(),
                        payment.destinationAccountId(),
                        payment.merchantId(),
                        payment.paymentMethod(),
                        payment.occurredAt());
    }

    private List<Document> searchSimilarProfiles(String context) {
        try {
            return vectorSearchClient.similarFraudProfiles(context, topK);
        } catch (RuntimeException exception) {
            log.warn("Vector similarity search failed; continuing with deterministic risk rules", exception);
            return List.of();
        }
    }

    private ParsedReasoning explainWithModel(PaymentPayload payment, RiskProfile riskProfile, String userPrompt) {
        if (!modelCallEnabled) {
            return new ParsedReasoning(fallbackReasoning(riskProfile));
        }
        try {
            String content = fraudAiClient.complete(SYSTEM_PROMPT, userPrompt);
            return parseReasoning(content);
        } catch (RuntimeException exception) {
            log.warn(
                    "AI explanation failed for paymentId={}; publishing deterministic {} decision",
                    payment.paymentId(),
                    riskProfile.decision(),
                    exception);
            return new ParsedReasoning(fallbackReasoning(riskProfile));
        }
    }

    private ParsedReasoning parseReasoning(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            throw new RetryableFraudEvaluationException("Gemini returned an empty reasoning response");
        }
        try {
            ParsedReasoning response = objectMapper.readValue(rawContent.trim(), ParsedReasoning.class);
            if (response.reasoning() == null || response.reasoning().isBlank()) {
                throw new RetryableFraudEvaluationException("Gemini reasoning is missing");
            }
            if (response.reasoning().length() > maxReasoningLength) {
                throw new RetryableFraudEvaluationException("Gemini reasoning exceeds configured length");
            }
            return new ParsedReasoning(response.reasoning().replaceAll("\\s+", " ").trim());
        } catch (JsonProcessingException exception) {
            throw new RetryableFraudEvaluationException("Gemini returned malformed JSON", exception);
        }
    }

    private static String buildUserPrompt(
            PaymentPayload payment,
            RiskProfile riskProfile,
            String context,
            String vectorSummary) {
        return """
                Fixed rules-engine decision:
                decision=%s
                riskScore=%d
                triggeredRules=%s

                Current transaction:
                paymentId=%s
                sourceAccountId=%s
                destinationAccountId=%s
                merchantId=%s
                amount=%s %s
                paymentMethod=%s
                occurredAt=%s

                Deterministic context string:
                %s

                Top similar historical records:
                %s
                """.formatted(
                riskProfile.decision(),
                riskProfile.totalScore(),
                riskProfile.triggeredRules().isEmpty() ? "[]" : riskProfile.triggeredRules(),
                payment.paymentId(),
                payment.accountId(),
                payment.destinationAccountId(),
                payment.merchantId(),
                payment.currency(),
                payment.amount().toPlainString(),
                payment.paymentMethod(),
                payment.occurredAt(),
                context,
                vectorSummary.isBlank() ? "No similar records were found." : vectorSummary);
    }

    private static String summarize(List<Document> documents) {
        return documents.stream()
                .limit(3)
                .map(document -> {
                    String score = document.getScore() == null
                            ? "n/a"
                            : String.format(Locale.ROOT, "%.4f", document.getScore());
                    return "- score=%s text=%s metadata=%s"
                            .formatted(score, bounded(document.getText(), 220), document.getMetadata());
                })
                .collect(Collectors.joining("\n"));
    }

    private static String bounded(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
    }

    private static String fallbackReasoning(RiskProfile riskProfile) {
        String rules = riskProfile.triggeredRules().isEmpty()
                ? "no high-risk rules were triggered"
                : String.join("; ", riskProfile.triggeredRules());
        return "Deterministic risk rules produced a %s decision with score %d because %s. AI explanation is temporarily unavailable."
                .formatted(riskProfile.decision(), riskProfile.totalScore(), bounded(rules, 320));
    }

    public record ParsedReasoning(String reasoning) {
    }
}
