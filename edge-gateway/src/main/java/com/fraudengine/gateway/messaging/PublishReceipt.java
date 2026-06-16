package com.fraudengine.gateway.messaging;

public record PublishReceipt(
        String topic,
        int partition,
        long offset) {
}
