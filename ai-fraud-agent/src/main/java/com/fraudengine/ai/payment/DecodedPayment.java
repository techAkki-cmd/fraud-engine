package com.fraudengine.ai.payment;

public record DecodedPayment(PaymentPayload payment, String correlationId) {
}
