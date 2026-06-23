CREATE TABLE ledger.audit_logs (
    audit_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id UUID,
    account_id VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    actor VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    balance_before NUMERIC(19, 2),
    balance_after NUMERIC(19, 2),
    CONSTRAINT ck_audit_action CHECK (action IN ('BALANCE_DEDUCTED', 'BALANCE_CREDITED', 'BALANCE_UPDATED')),
    CONSTRAINT ck_audit_balance_changed CHECK (
        balance_before IS NULL
        OR balance_after IS NULL
        OR balance_before <> balance_after
    )
);

CREATE INDEX ix_audit_logs_payment_created
    ON ledger.audit_logs (payment_id, created_at DESC);

CREATE INDEX ix_audit_logs_account_created
    ON ledger.audit_logs (account_id, created_at DESC);

CREATE OR REPLACE FUNCTION ledger.reject_audit_log_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'ledger.audit_logs is immutable';
END;
$$;

CREATE TRIGGER trg_audit_logs_immutable_update
    BEFORE UPDATE ON ledger.audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION ledger.reject_audit_log_mutation();

CREATE TRIGGER trg_audit_logs_immutable_delete
    BEFORE DELETE ON ledger.audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION ledger.reject_audit_log_mutation();

CREATE OR REPLACE FUNCTION ledger.audit_account_balance_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    audit_payment_id UUID;
    audit_actor TEXT;
    audit_action TEXT;
BEGIN
    IF OLD.balance IS NOT DISTINCT FROM NEW.balance THEN
        RETURN NEW;
    END IF;

    audit_payment_id := NULLIF(current_setting('app.audit_payment_id', true), '')::UUID;
    audit_actor := COALESCE(NULLIF(current_setting('app.audit_actor', true), ''), 'system');
    audit_action := CASE
        WHEN NEW.balance < OLD.balance THEN 'BALANCE_DEDUCTED'
        WHEN NEW.balance > OLD.balance THEN 'BALANCE_CREDITED'
        ELSE 'BALANCE_UPDATED'
    END;

    INSERT INTO ledger.audit_logs (
        payment_id,
        account_id,
        action,
        actor,
        balance_before,
        balance_after
    )
    VALUES (
        audit_payment_id,
        NEW.account_id,
        audit_action,
        audit_actor,
        OLD.balance,
        NEW.balance
    );

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_accounts_audit_balance_update
    AFTER UPDATE OF balance ON ledger.accounts
    FOR EACH ROW
    EXECUTE FUNCTION ledger.audit_account_balance_update();
