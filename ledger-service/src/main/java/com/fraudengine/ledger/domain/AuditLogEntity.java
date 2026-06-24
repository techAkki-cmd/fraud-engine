package com.fraudengine.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "audit_logs", schema = "ledger")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id", nullable = false, updatable = false)
    private Long auditId;

    @Column(name = "payment_id", updatable = false)
    private UUID paymentId;

    @Column(name = "account_id", length = 64, nullable = false, updatable = false)
    private String accountId;

    @Column(name = "action", length = 64, nullable = false, updatable = false)
    private String action;

    @Column(name = "actor", length = 128, nullable = false, updatable = false)
    private String actor;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant timestamp;

    @Column(name = "balance_before", precision = 19, scale = 2, updatable = false)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", precision = 19, scale = 2, updatable = false)
    private BigDecimal balanceAfter;

    protected AuditLogEntity() {
    }

    public Long getAuditId() {
        return auditId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getAction() {
        return action;
    }

    public String getActor() {
        return actor;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public BigDecimal getBalanceBefore() {
        return balanceBefore;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }
}
