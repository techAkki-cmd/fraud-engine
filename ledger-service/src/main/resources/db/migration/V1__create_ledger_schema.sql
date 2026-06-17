CREATE SCHEMA IF NOT EXISTS ledger;

CREATE TABLE ledger.accounts (
    account_id VARCHAR(64) PRIMARY KEY,
    balance NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_accounts_non_negative_balance CHECK (balance >= 0),
    CONSTRAINT ck_accounts_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE TABLE ledger.ledger_entries (
    entry_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL UNIQUE,
    source_account_id VARCHAR(64) NOT NULL,
    destination_account_id VARCHAR(64) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    failure_code VARCHAR(64),
    failure_reason VARCHAR(512),
    correlation_id VARCHAR(128) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ledger_entries_positive_amount CHECK (amount > 0),
    CONSTRAINT ck_ledger_entries_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_ledger_entries_status CHECK (status IN ('SUCCESS', 'FAILED')),
    CONSTRAINT ck_ledger_entries_failure_details CHECK (
        (status = 'SUCCESS' AND failure_code IS NULL AND failure_reason IS NULL)
        OR
        (status = 'FAILED' AND failure_code IS NOT NULL AND failure_reason IS NOT NULL)
    )
);

CREATE INDEX ix_ledger_entries_source_processed
    ON ledger.ledger_entries (source_account_id, processed_at DESC);

CREATE INDEX ix_ledger_entries_destination_processed
    ON ledger.ledger_entries (destination_account_id, processed_at DESC);

CREATE TABLE ledger.idempotency_keys (
    payment_id UUID PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    claim_token UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_idempotency_status CHECK (status IN ('PROCESSING', 'COMPLETED')),
    CONSTRAINT ck_idempotency_claim_token CHECK (
        (status = 'PROCESSING' AND claim_token IS NOT NULL)
        OR
        (status = 'COMPLETED' AND claim_token IS NULL)
    )
);

CREATE INDEX ix_idempotency_processing_updated
    ON ledger.idempotency_keys (updated_at)
    WHERE status = 'PROCESSING';
