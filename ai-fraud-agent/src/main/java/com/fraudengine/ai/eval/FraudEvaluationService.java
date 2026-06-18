package com.fraudengine.ai.eval;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudengine.ai.decision.FraudDecision;
import com.fraudengine.ai.decision.FraudEvaluationResult;
import com.fraudengine.ai.payment.PaymentPayload;
import com.fraudengine.ai.support.RetryableFraudEvaluationException;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FraudEvaluationService {

    private static final String SYSTEM_PROMPT = """
            You are a senior financial risk assessor for a core banking payment integrity system.
            Evaluate only the provided transaction and similar historical records.
            Return only strict JSON with this exact schema:
            {"decision":"SAFE|FRAUD","reasoning":"short operational reason"}
            Do not include markdown, code fences, extra keys, comments, or prose outside JSON.
            Choose FRAUD when the transaction resembles high-risk historical scenarios or contains unusual risk signals.
            Choose SAFE only when risk indicators are weak or absent.
            """;

    private final VectorSearchClient vectorSearchClient;
    private final FraudAiClient fraudAiClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String modelName;
    private final int topK;
    private final int maxReasoningLength;

    public FraudEvaluationService(
            VectorSearchClient vectorSearchClient,
            FraudAiClient fraudAiClient,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${app.ai.provider-name}") String modelName,
            @Value("${app.ai.vector-top-k:3}") int topK,
            @Value("${app.ai.max-reasoning-length:512}") int maxReasoningLength,
            @Value("${app.ai.decision-timeout:20s}") Duration decisionTimeout) {
        this.vectorSearchClient = vectorSearchClient;
        this.fraudAiClient = fraudAiClient;
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        this.clock = clock;
        this.modelName = modelName;
        this.topK = topK;
        this.maxReasoningLength = maxReasoningLength;
    }

    public FraudEvaluationResult evaluate(PaymentPayload payment) {
        String context = buildContext(payment);
        List<Document> similarDocuments = searchSimilarProfiles(context);
        String vectorSummary = summarize(similarDocuments);
        String userPrompt = buildUserPrompt(payment, context, vectorSummary);
        String content = callModel(userPrompt);
        ParsedDecision parsedDecision = parseDecision(content);
        return new FraudEvaluationResult(
                parsedDecision.decision(),
                parsedDecision.reasoning(),
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
            throw new RetryableFraudEvaluationException("Vector similarity search failed", exception);
        }
    }

    private String callModel(String userPrompt) {
        try {
            return fraudAiClient.complete(SYSTEM_PROMPT, userPrompt);
        } catch (RuntimeException exception) {
            throw new RetryableFraudEvaluationException("Gemini fraud decision call failed", exception);
        }
    }

    private ParsedDecision parseDecision(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            throw new RetryableFraudEvaluationException("Gemini returned an empty decision");
        }
        try {
            ParsedDecision decision = objectMapper.readValue(rawContent.trim(), ParsedDecision.class);
            if (decision.decision() == null) {
                throw new RetryableFraudEvaluationException("Gemini decision is missing");
            }
            if (decision.reasoning() == null || decision.reasoning().isBlank()) {
                throw new RetryableFraudEvaluationException("Gemini reasoning is missing");
            }
            if (decision.reasoning().length() > maxReasoningLength) {
                throw new RetryableFraudEvaluationException("Gemini reasoning exceeds configured length");
            }
            return new ParsedDecision(
                    decision.decision(),
                    decision.reasoning().replaceAll("\\s+", " ").trim());
        } catch (JsonProcessingException exception) {
            throw new RetryableFraudEvaluationException("Gemini returned malformed JSON", exception);
        }
    }

    private static String buildUserPrompt(PaymentPayload payment, String context, String vectorSummary) {
        return """
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

    public record ParsedDecision(FraudDecision decision, String reasoning) {
    }
}
