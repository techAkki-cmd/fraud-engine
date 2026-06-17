package com.fraudengine.ledger.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.fraudengine.ledger.domain.IdempotencyKey;
import com.fraudengine.ledger.domain.IdempotencyStatus;
import com.fraudengine.ledger.exception.ActivePaymentClaimException;
import com.fraudengine.ledger.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentClaimServiceTests {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private IdempotencyKeyRepository repository;
    private PaymentClaimService service;

    @BeforeEach
    void setUp() {
        repository = mock(IdempotencyKeyRepository.class);
        service = new PaymentClaimService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(2));
    }

    @Test
    void acquiresANewClaimWithAnOwnershipToken() {
        UUID paymentId = UUID.randomUUID();
        when(repository.insertClaim(eq(paymentId), any(UUID.class), eq(NOW))).thenReturn(1);

        PaymentClaim claim = service.claim(paymentId);

        assertThat(claim.outcome()).isEqualTo(ClaimOutcome.ACQUIRED);
        assertThat(claim.claimToken()).isNotNull();
    }

    @Test
    void treatsCompletedRowsAsDuplicates() {
        UUID paymentId = UUID.randomUUID();
        IdempotencyKey completed = key(IdempotencyStatus.COMPLETED);
        when(repository.findById(paymentId)).thenReturn(Optional.of(completed));

        PaymentClaim claim = service.claim(paymentId);

        assertThat(claim.isCompleted()).isTrue();
        verify(repository, never()).reclaimStale(eq(paymentId), any(), any(), any());
    }

    @Test
    void reclaimsOnlyStaleProcessingRows() {
        UUID paymentId = UUID.randomUUID();
        IdempotencyKey processing = key(IdempotencyStatus.PROCESSING);
        when(repository.findById(paymentId)).thenReturn(Optional.of(processing));
        when(repository.reclaimStale(
                eq(paymentId),
                any(UUID.class),
                eq(NOW),
                eq(NOW.minus(Duration.ofMinutes(2)))))
                .thenReturn(1);

        PaymentClaim claim = service.claim(paymentId);

        assertThat(claim.outcome()).isEqualTo(ClaimOutcome.ACQUIRED);
    }

    @Test
    void rejectsFreshClaimsOwnedByAnotherInstance() {
        UUID paymentId = UUID.randomUUID();
        IdempotencyKey processing = key(IdempotencyStatus.PROCESSING);
        when(repository.findById(paymentId)).thenReturn(Optional.of(processing));

        assertThatThrownBy(() -> service.claim(paymentId))
                .isInstanceOf(ActivePaymentClaimException.class);
    }

    @Test
    void releasesClaimsUsingBothPaymentIdAndOwnershipToken() {
        PaymentClaim claim = PaymentClaim.acquired(UUID.randomUUID(), UUID.randomUUID());

        service.release(claim);

        verify(repository).deleteOwnedProcessingClaim(claim.paymentId(), claim.claimToken());
    }

    private static IdempotencyKey key(IdempotencyStatus status) {
        IdempotencyKey key = mock(IdempotencyKey.class);
        when(key.getStatus()).thenReturn(status);
        return key;
    }
}
