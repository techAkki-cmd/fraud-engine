package com.fraudengine.ai.messaging;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fraudengine.ai.decision.DecisionClaim;
import com.fraudengine.ai.decision.FraudDecision;
import com.fraudengine.ai.decision.FraudDecisionRepository;
import com.fraudengine.ai.decision.FraudEvaluationResult;
import com.fraudengine.ai.eval.FraudEvaluationService;
import com.fraudengine.ai.payment.DecodedPayment;
import com.fraudengine.ai.payment.PaymentEventDecoder;
import com.fraudengine.ai.payment.PaymentMethod;
import com.fraudengine.ai.payment.PaymentPayload;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class FraudAgentConsumerTests {

    private final PaymentEventDecoder decoder = org.mockito.Mockito.mock(PaymentEventDecoder.class);
    private final FraudDecisionRepository repository = org.mockito.Mockito.mock(FraudDecisionRepository.class);
    private final FraudEvaluationService service = org.mockito.Mockito.mock(FraudEvaluationService.class);
    private final FraudDecisionPublisher publisher = org.mockito.Mockito.mock(FraudDecisionPublisher.class);
    private final Acknowledgment acknowledgment = org.mockito.Mockito.mock(Acknowledgment.class);

    @Test
    void evaluatesPersistsPublishesAndAcknowledgesNewPayment() {
        FraudAgentConsumer consumer = new FraudAgentConsumer(decoder, repository, service, publisher);
        ConsumerRecord<String, String> record = record();
        DecodedPayment decoded = new DecodedPayment(payment(), "corr-123");
        FraudEvaluationResult result = result(FraudDecision.SAFE);
        when(decoder.decode(record)).thenReturn(decoded);
        when(repository.claim(payment().paymentId(), "corr-123", record)).thenReturn(DecisionClaim.acquired(payment().paymentId()));
        when(service.evaluate(payment())).thenReturn(result);

        consumer.consume(record, acknowledgment);

        verify(repository).complete(payment().paymentId(), result);
        verify(publisher).publish(record, decoded, result);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void duplicateCompletedPaymentRepublishesStoredDecisionWithoutCallingAi() {
        FraudAgentConsumer consumer = new FraudAgentConsumer(decoder, repository, service, publisher);
        ConsumerRecord<String, String> record = record();
        DecodedPayment decoded = new DecodedPayment(payment(), "corr-123");
        FraudEvaluationResult result = result(FraudDecision.FRAUD);
        when(decoder.decode(record)).thenReturn(decoded);
        when(repository.claim(payment().paymentId(), "corr-123", record))
                .thenReturn(DecisionClaim.completed(payment().paymentId(), result));

        consumer.consume(record, acknowledgment);

        verify(service, never()).evaluate(payment());
        verify(repository, never()).complete(payment().paymentId(), result);
        verify(publisher).publish(record, decoded, result);
        verify(acknowledgment).acknowledge();
    }

    private static ConsumerRecord<String, String> record() {
        return new ConsumerRecord<>(PaymentTopics.PAYMENT_INGEST, 0, 10L, payment().paymentId().toString(), "{}");
    }

    private static PaymentPayload payment() {
        return new PaymentPayload(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "acct-source",
                "acct-destination",
                "merchant-42",
                new BigDecimal("125.25"),
                "USD",
                PaymentMethod.CARD,
                Instant.parse("2024-01-01T00:00:00Z"));
    }

    private static FraudEvaluationResult result(FraudDecision decision) {
        return new FraudEvaluationResult(
                decision,
                "reason",
                "mock-ai",
                "matches",
                Instant.parse("2024-01-01T00:00:02Z"));
    }
}
