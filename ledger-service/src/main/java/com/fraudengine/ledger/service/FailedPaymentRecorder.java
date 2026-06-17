package com.fraudengine.ledger.service;

import java.math.BigDecimal;
import java.time.Clock;

import com.fraudengine.ledger.domain.LedgerEntry;
import com.fraudengine.ledger.exception.BusinessRuleException;
import com.fraudengine.ledger.idempotency.PaymentClaim;
import com.fraudengine.ledger.idempotency.PaymentClaimService;
import com.fraudengine.ledger.messaging.PaymentEvent;
import com.fraudengine.ledger.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FailedPaymentRecorder {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaymentClaimService claimService;
    private final Clock clock;

    public FailedPaymentRecorder(
            LedgerEntryRepository ledgerEntryRepository,
            PaymentClaimService claimService,
            Clock clock) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.claimService = claimService;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            PaymentEvent payment,
            String correlationId,
            PaymentClaim claim,
            BusinessRuleException failure) {
        BigDecimal amount = MoneyPolicy.normalize(payment.amount());
        ledgerEntryRepository.save(LedgerEntry.failed(
                payment.paymentId(),
                payment.accountId(),
                payment.destinationAccountId(),
                amount,
                payment.currency(),
                failure.getFailureCode(),
                failure.getMessage(),
                correlationId,
                clock.instant()));
        claimService.completeOwnedClaim(claim);
    }
}
