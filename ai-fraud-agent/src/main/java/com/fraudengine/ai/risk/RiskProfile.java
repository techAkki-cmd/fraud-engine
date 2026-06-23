package com.fraudengine.ai.risk;

import java.util.List;

import com.fraudengine.ai.decision.FraudDecision;

public record RiskProfile(int totalScore, FraudDecision decision, List<String> triggeredRules) {

    public RiskProfile {
        if (totalScore < 0 || totalScore > 100) {
            throw new IllegalArgumentException("totalScore must be within 0..100");
        }
        triggeredRules = List.copyOf(triggeredRules);
    }

    public boolean isFraud() {
        return decision == FraudDecision.BLOCK;
    }
}
