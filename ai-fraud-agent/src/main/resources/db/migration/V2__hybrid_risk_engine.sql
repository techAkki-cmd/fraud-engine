ALTER TABLE fraud_agent.fraud_decisions
    DROP CONSTRAINT IF EXISTS ck_fraud_decisions_decision;

ALTER TABLE fraud_agent.fraud_decisions
    ADD CONSTRAINT ck_fraud_decisions_decision
        CHECK (decision IS NULL OR decision IN ('SAFE', 'FRAUD', 'REVIEW', 'BLOCK'));

UPDATE fraud_agent.fraud_decisions
SET decision = 'BLOCK'
WHERE decision = 'FRAUD';

ALTER TABLE fraud_agent.fraud_decisions
    DROP CONSTRAINT IF EXISTS ck_fraud_decisions_decision;

ALTER TABLE fraud_agent.fraud_decisions
    ADD CONSTRAINT ck_fraud_decisions_decision
        CHECK (decision IS NULL OR decision IN ('SAFE', 'REVIEW', 'BLOCK'));

ALTER TABLE fraud_agent.fraud_decisions
    ADD COLUMN IF NOT EXISTS risk_score INTEGER,
    ADD COLUMN IF NOT EXISTS rules_triggered TEXT;

ALTER TABLE fraud_agent.fraud_decisions
    DROP CONSTRAINT IF EXISTS ck_fraud_decisions_risk_score;

ALTER TABLE fraud_agent.fraud_decisions
    ADD CONSTRAINT ck_fraud_decisions_risk_score
        CHECK (risk_score IS NULL OR risk_score BETWEEN 0 AND 100);
