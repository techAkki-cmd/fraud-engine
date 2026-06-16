package com.fraudengine.gateway.api;

import java.util.UUID;

public record PaymentAcceptedResponse(
        UUID paymentId,
        String correlationId,
        String status) {
}
