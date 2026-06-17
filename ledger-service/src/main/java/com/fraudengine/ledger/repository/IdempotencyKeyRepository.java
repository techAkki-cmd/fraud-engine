package com.fraudengine.ledger.repository;

import java.time.Instant;
import java.util.UUID;

import com.fraudengine.ledger.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    @Modifying
    @Query(value = """
            insert into ledger.idempotency_keys
                (payment_id, status, claim_token, created_at, updated_at)
            values
                (:paymentId, 'PROCESSING', :claimToken, :now, :now)
            on conflict (payment_id) do nothing
            """, nativeQuery = true)
    int insertClaim(
            @Param("paymentId") UUID paymentId,
            @Param("claimToken") UUID claimToken,
            @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update ledger.idempotency_keys
            set claim_token = :claimToken,
                updated_at = :now
            where payment_id = :paymentId
              and status = 'PROCESSING'
              and updated_at <= :staleBefore
            """, nativeQuery = true)
    int reclaimStale(
            @Param("paymentId") UUID paymentId,
            @Param("claimToken") UUID claimToken,
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update ledger.idempotency_keys
            set status = 'COMPLETED',
                claim_token = null,
                updated_at = :now
            where payment_id = :paymentId
              and status = 'PROCESSING'
              and claim_token = :claimToken
            """, nativeQuery = true)
    int completeClaim(
            @Param("paymentId") UUID paymentId,
            @Param("claimToken") UUID claimToken,
            @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            delete from ledger.idempotency_keys
            where payment_id = :paymentId
              and status = 'PROCESSING'
              and claim_token = :claimToken
            """, nativeQuery = true)
    int deleteOwnedProcessingClaim(
            @Param("paymentId") UUID paymentId,
            @Param("claimToken") UUID claimToken);
}
