package com.fraudengine.ai.decision;

import java.time.Instant;
import java.util.List;

public record FraudEvaluationResult(
        FraudDecision decision,
        int riskScore,
        List<String> rulesTriggered,
        String reasoning,
        String model,
        String vectorMatches,
        Instant evaluatedAt) {

    public FraudEvaluationResult {
        rulesTriggered = List.copyOf(rulesTriggered);
    }
}
