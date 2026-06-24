package com.fraudengine.ledger.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fraudengine.ledger.domain.FailureCode;
import com.fraudengine.ledger.exception.BusinessRuleException;
import com.fraudengine.ledger.idempotency.PaymentClaim;
import com.fraudengine.ledger.idempotency.PaymentClaimService;
import com.fraudengine.ledger.service.FailedPaymentRecorder;
import com.fraudengine.ledger.service.LedgerService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentConsumerTests {

    private PaymentEventDecoder decoder;
    private PaymentClaimService claimService;
    private LedgerService ledgerService;
    private FailedPaymentRecorder failedPaymentRecorder;
    private Acknowledgment acknowledgment;
    private PaymentConsumer consumer;
    private ConsumerRecord<String, String> record;
    private PaymentEvent payment;

    @BeforeEach
    void setUp() {
        decoder = mock(PaymentEventDecoder.class);
        claimService = mock(PaymentClaimService.class);
        ledgerService = mock(LedgerService.class);
        failedPaymentRecorder = mock(FailedPaymentRecorder.class);
        acknowledgment = mock(Acknowledgment.class);
        consumer = new PaymentConsumer(decoder, claimService, ledgerService, failedPaymentRecorder);
        record = new ConsumerRecord<>(PaymentTopics.PAYMENT_CLEARED, 0, 10L, "key", "{}");
        payment = payment();
        when(decoder.decode(record)).thenReturn(new DecodedPayment(payment, "correlation-1"));
    }

    @Test
    void acknowledgesOnlyAfterSuccessfulLedgerProcessing() {
        PaymentClaim claim = PaymentClaim.acquired(payment.paymentId(), UUID.randomUUID());
        when(claimService.claim(payment.paymentId())).thenReturn(claim);

        consumer.consume(record, acknowledgment);

        verify(ledgerService).processPayment(payment, "correlation-1", claim);
        verify(acknowledgment).acknowledge();
        verify(claimService, never()).release(claim);
    }

    @Test
    void recordsTerminalBusinessFailuresAndAcknowledgesThem() {
        PaymentClaim claim = PaymentClaim.acquired(payment.paymentId(), UUID.randomUUID());
        BusinessRuleException failure = new BusinessRuleException(
                FailureCode.INSUFFICIENT_FUNDS,
                "insufficient funds");
        when(claimService.claim(payment.paymentId())).thenReturn(claim);
        doThrow(failure).when(ledgerService).processPayment(payment, "correlation-1", claim);

        consumer.consume(record, acknowledgment);

        verify(failedPaymentRecorder).record(payment, "correlation-1", claim, failure);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void releasesOwnedClaimsAndDoesNotAcknowledgeTechnicalFailures() {
        PaymentClaim claim = PaymentClaim.acquired(payment.paymentId(), UUID.randomUUID());
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(claimService.claim(payment.paymentId())).thenReturn(claim);
        doThrow(failure).when(ledgerService).processPayment(payment, "correlation-1", claim);

        assertThatThrownBy(() -> consumer.consume(record, acknowledgment))
                .isSameAs(failure);

        verify(claimService).release(claim);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void acknowledgesAlreadyCompletedDuplicatesWithoutPostingAgain() {
        PaymentClaim claim = PaymentClaim.completed(payment.paymentId());
        when(claimService.claim(payment.paymentId())).thenReturn(claim);

        consumer.consume(record, acknowledgment);

        verify(ledgerService, never()).processPayment(payment, "correlation-1", claim);
        verify(acknowledgment).acknowledge();
    }

    private static PaymentEvent payment() {
        return new PaymentEvent(
                UUID.randomUUID(),
                "account-1",
                "account-2",
                "merchant-1",
                new BigDecimal("25.50"),
                "USD",
                PaymentMethod.CARD,
                Instant.now().minusSeconds(1),
                null);
    }
}
