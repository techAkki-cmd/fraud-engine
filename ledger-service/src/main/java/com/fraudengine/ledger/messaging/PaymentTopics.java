package com.fraudengine.ledger.messaging;

public final class PaymentTopics {

    public static final String PAYMENT_INGEST = "payment-ingest";
    public static final String PAYMENT_INGEST_DLT = "payment-ingest.DLT";
    public static final String PAYMENT_CLEARED = "payment-cleared";
    public static final String PAYMENT_CLEARED_DLT = "payment-cleared.DLT";

    private PaymentTopics() {
    }
}
