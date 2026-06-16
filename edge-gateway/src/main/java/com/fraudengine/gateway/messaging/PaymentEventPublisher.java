package com.fraudengine.gateway.messaging;

import com.fraudengine.gateway.api.PaymentRequest;
import reactor.core.publisher.Mono;

public interface PaymentEventPublisher {

    Mono<PublishReceipt> publish(PaymentRequest payment, String correlationId);
}
