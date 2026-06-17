package com.fraudengine.ledger.exception;

import java.util.UUID;

public class ActivePaymentClaimException extends RuntimeException {

    public ActivePaymentClaimException(UUID paymentId) {
        super("Payment %s is already being processed by another ledger instance".formatted(paymentId));
    }
}
