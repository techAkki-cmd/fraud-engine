package com.fraudengine.ledger.repository;

import com.fraudengine.ledger.domain.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
}
