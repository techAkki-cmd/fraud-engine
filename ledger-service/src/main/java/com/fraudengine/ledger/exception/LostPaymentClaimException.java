package com.fraudengine.ledger.exception;

import java.util.UUID;

public class LostPaymentClaimException extends RuntimeException {

    public LostPaymentClaimException(UUID paymentId) {
        super("The processing claim for payment %s is no longer owned by this instance".formatted(paymentId));
    }
}
