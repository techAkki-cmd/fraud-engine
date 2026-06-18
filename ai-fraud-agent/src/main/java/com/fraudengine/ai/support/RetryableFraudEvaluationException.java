package com.fraudengine.ai.support;

public class RetryableFraudEvaluationException extends RuntimeException {

    public RetryableFraudEvaluationException(String message) {
        super(message);
    }

    public RetryableFraudEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
