package com.fraudengine.ai.decision;

import java.time.Instant;

public record FraudEvaluationResult(
        FraudDecision decision,
        String reasoning,
        String model,
        String vectorMatches,
        Instant evaluatedAt) {
}
