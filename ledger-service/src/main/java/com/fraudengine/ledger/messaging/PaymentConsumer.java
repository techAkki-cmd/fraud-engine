package com.fraudengine.ledger.messaging;

import com.fraudengine.ledger.exception.BusinessRuleException;
import com.fraudengine.ledger.idempotency.PaymentClaim;
import com.fraudengine.ledger.idempotency.PaymentClaimService;
import com.fraudengine.ledger.service.FailedPaymentRecorder;
import com.fraudengine.ledger.service.LedgerService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class PaymentConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentConsumer.class);

    private final PaymentEventDecoder decoder;
    private final PaymentClaimService claimService;
    private final LedgerService ledgerService;
    private final FailedPaymentRecorder failedPaymentRecorder;

    public PaymentConsumer(
            PaymentEventDecoder decoder,
            PaymentClaimService claimService,
            LedgerService ledgerService,
            FailedPaymentRecorder failedPaymentRecorder) {
        this.decoder = decoder;
        this.claimService = claimService;
        this.ledgerService = ledgerService;
        this.failedPaymentRecorder = failedPaymentRecorder;
    }

    @KafkaListener(
            topics = PaymentTopics.PAYMENT_CLEARED,
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        DecodedPayment decoded = decoder.decode(record);
        PaymentEvent payment = decoded.payment();
        PaymentClaim claim = claimService.claim(payment.paymentId());

        if (claim.isCompleted()) {
            log.info(
                    "Skipping completed duplicate paymentId={} topic={} partition={} offset={}",
                    payment.paymentId(),
                    record.topic(),
                    record.partition(),
                    record.offset());
            acknowledgment.acknowledge();
            return;
        }

        try {
            try {
                ledgerService.processPayment(payment, decoded.correlationId(), claim);
            } catch (BusinessRuleException businessFailure) {
                failedPaymentRecorder.record(
                        payment,
                        decoded.correlationId(),
                        claim,
                        businessFailure);
                log.info(
                        "Recorded terminal payment failure paymentId={} failureCode={}",
                        payment.paymentId(),
                        businessFailure.getFailureCode());
            }
        } catch (RuntimeException processingFailure) {
            claimService.release(claim);
            throw processingFailure;
        }

        acknowledgment.acknowledge();
        log.info(
                "Committed payment outcome paymentId={} topic={} partition={} offset={}",
                payment.paymentId(),
                record.topic(),
                record.partition(),
                record.offset());
    }
}
