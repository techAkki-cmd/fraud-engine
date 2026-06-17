package com.fraudengine.ledger.idempotency;

import java.util.Objects;
import java.util.UUID;

public record PaymentClaim(UUID paymentId, UUID claimToken, ClaimOutcome outcome) {

    public PaymentClaim {
        Objects.requireNonNull(paymentId);
        Objects.requireNonNull(outcome);
        if (outcome == ClaimOutcome.ACQUIRED) {
            Objects.requireNonNull(claimToken);
        } else if (claimToken != null) {
            throw new IllegalArgumentException("A completed claim cannot have an ownership token");
        }
    }

    public static PaymentClaim acquired(UUID paymentId, UUID claimToken) {
        return new PaymentClaim(paymentId, claimToken, ClaimOutcome.ACQUIRED);
    }

    public static PaymentClaim completed(UUID paymentId) {
        return new PaymentClaim(paymentId, null, ClaimOutcome.COMPLETED);
    }

    public boolean isCompleted() {
        return outcome == ClaimOutcome.COMPLETED;
    }
}
