package com.fraudengine.ledger.messaging;

public record DecodedPayment(PaymentEvent payment, String correlationId) {
}
