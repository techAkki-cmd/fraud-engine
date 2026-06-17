package com.fraudengine.ledger.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.fraudengine.ledger.domain.IdempotencyKey;
import com.fraudengine.ledger.domain.IdempotencyStatus;
import com.fraudengine.ledger.exception.ActivePaymentClaimException;
import com.fraudengine.ledger.exception.LostPaymentClaimException;
import com.fraudengine.ledger.repository.IdempotencyKeyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentClaimService {

    private final IdempotencyKeyRepository repository;
    private final Clock clock;
    private final Duration processingLease;

    public PaymentClaimService(
            IdempotencyKeyRepository repository,
            Clock clock,
            @Value("${fraud-engine.ledger.processing-lease:2m}") Duration processingLease) {
        this.repository = repository;
        this.clock = clock;
        this.processingLease = processingLease;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentClaim claim(UUID paymentId) {
        Instant now = clock.instant();
        UUID claimToken = UUID.randomUUID();

        if (repository.insertClaim(paymentId, claimToken, now) == 1) {
            return PaymentClaim.acquired(paymentId, claimToken);
        }

        IdempotencyKey existing = repository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency row disappeared while claiming payment " + paymentId));
        if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
            return PaymentClaim.completed(paymentId);
        }

        Instant staleBefore = now.minus(processingLease);
        if (repository.reclaimStale(paymentId, claimToken, now, staleBefore) == 1) {
            return PaymentClaim.acquired(paymentId, claimToken);
        }

        IdempotencyKey current = repository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency row disappeared while checking payment " + paymentId));
        if (current.getStatus() == IdempotencyStatus.COMPLETED) {
            return PaymentClaim.completed(paymentId);
        }
        throw new ActivePaymentClaimException(paymentId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(PaymentClaim claim) {
        if (!claim.isCompleted()) {
            repository.deleteOwnedProcessingClaim(claim.paymentId(), claim.claimToken());
        }
    }

    public void completeOwnedClaim(PaymentClaim claim) {
        if (claim.isCompleted()
                || repository.completeClaim(claim.paymentId(), claim.claimToken(), clock.instant()) != 1) {
            throw new LostPaymentClaimException(claim.paymentId());
        }
    }
}
