package com.fraudengine.ai.decision;

import java.util.UUID;

public record DecisionClaim(UUID paymentId, boolean completed, FraudEvaluationResult result) {

    public static DecisionClaim acquired(UUID paymentId) {
        return new DecisionClaim(paymentId, false, null);
    }

    public static DecisionClaim completed(UUID paymentId, FraudEvaluationResult result) {
        return new DecisionClaim(paymentId, true, result);
    }
}
