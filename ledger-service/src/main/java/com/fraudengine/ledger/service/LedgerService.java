package com.fraudengine.ledger.service;

import com.fraudengine.ledger.idempotency.PaymentClaim;
import com.fraudengine.ledger.messaging.PaymentEvent;

public interface LedgerService {

    void processPayment(PaymentEvent payment, String correlationId, PaymentClaim claim);
}
