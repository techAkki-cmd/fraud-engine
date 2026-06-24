package com.fraudengine.ai.messaging;

public final class PaymentTopics {

    public static final String PAYMENT_INGEST = "payment-ingest";
    public static final String PAYMENT_CLEARED = "payment-cleared";
    public static final String PAYMENT_REVIEW = "payment-review";
    public static final String PAYMENT_BLOCKED = "payment-blocked";
    public static final String PAYMENT_INGEST_DLT = "payment-ingest.DLT";

    private PaymentTopics() {
    }
}
