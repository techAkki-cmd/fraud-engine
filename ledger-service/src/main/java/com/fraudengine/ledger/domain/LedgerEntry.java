package com.fraudengine.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_entries", schema = "ledger")
public class LedgerEntry {

    @Id
    @Column(name = "entry_id", nullable = false, updatable = false)
    private UUID entryId;

    @Column(name = "payment_id", nullable = false, unique = true, updatable = false)
    private UUID paymentId;

    @Column(name = "source_account_id", length = 64, nullable = false, updatable = false)
    private String sourceAccountId;

    @Column(name = "destination_account_id", length = 64, nullable = false, updatable = false)
    private String destinationAccountId;

    @Column(name = "amount", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false, updatable = false)
    private LedgerEntryStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 64, updatable = false)
    private FailureCode failureCode;

    @Column(name = "failure_reason", length = 512, updatable = false)
    private String failureReason;

    @Column(name = "correlation_id", length = 128, nullable = false, updatable = false)
    private String correlationId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected LedgerEntry() {
    }

    private LedgerEntry(
            UUID paymentId,
            String sourceAccountId,
            String destinationAccountId,
            BigDecimal amount,
            String currency,
            LedgerEntryStatus status,
            FailureCode failureCode,
            String failureReason,
            String correlationId,
            Instant processedAt) {
        this.entryId = UUID.randomUUID();
        this.paymentId = Objects.requireNonNull(paymentId);
        this.sourceAccountId = Objects.requireNonNull(sourceAccountId);
        this.destinationAccountId = Objects.requireNonNull(destinationAccountId);
        this.amount = Objects.requireNonNull(amount);
        this.currency = Objects.requireNonNull(currency);
        this.status = Objects.requireNonNull(status);
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.correlationId = Objects.requireNonNull(correlationId);
        this.processedAt = Objects.requireNonNull(processedAt);
    }

    public static LedgerEntry success(
            UUID paymentId,
            String sourceAccountId,
            String destinationAccountId,
            BigDecimal amount,
            String currency,
            String correlationId,
            Instant processedAt) {
        return new LedgerEntry(
                paymentId,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                LedgerEntryStatus.SUCCESS,
                null,
                null,
                correlationId,
                processedAt);
    }

    public static LedgerEntry failed(
            UUID paymentId,
            String sourceAccountId,
            String destinationAccountId,
            BigDecimal amount,
            String currency,
            FailureCode failureCode,
            String failureReason,
            String correlationId,
            Instant processedAt) {
        return new LedgerEntry(
                paymentId,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                LedgerEntryStatus.FAILED,
                Objects.requireNonNull(failureCode),
                Objects.requireNonNull(failureReason),
                correlationId,
                processedAt);
    }

    public UUID getEntryId() {
        return entryId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getSourceAccountId() {
        return sourceAccountId;
    }

    public String getDestinationAccountId() {
        return destinationAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public LedgerEntryStatus getStatus() {
        return status;
    }

    public FailureCode getFailureCode() {
        return failureCode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
