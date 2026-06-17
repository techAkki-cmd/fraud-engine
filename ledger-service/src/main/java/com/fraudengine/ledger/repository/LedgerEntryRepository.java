package com.fraudengine.ledger.repository;

import java.util.Optional;
import java.util.UUID;

import com.fraudengine.ledger.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    Optional<LedgerEntry> findByPaymentId(UUID paymentId);
}
