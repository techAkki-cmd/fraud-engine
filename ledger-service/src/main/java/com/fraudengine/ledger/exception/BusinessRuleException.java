package com.fraudengine.ledger.exception;

import com.fraudengine.ledger.domain.FailureCode;

public class BusinessRuleException extends RuntimeException {

    private final FailureCode failureCode;

    public BusinessRuleException(FailureCode failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public FailureCode getFailureCode() {
        return failureCode;
    }
}
