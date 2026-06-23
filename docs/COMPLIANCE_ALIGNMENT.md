# Compliance Alignment: Fintech Security Framework

## Compliance Boundary

The compliance boundary covers Gateway ingestion, Kafka transport, AI fraud evaluation, ledger mutation, PostgreSQL persistence, Vault-backed configuration, and stdout logging. The dashboard is also part of the confidentiality boundary because it shows account identifiers, fraud reasoning, and live payment events.

This document describes alignment, not certification. Production compliance still depends on cloud controls, operating procedures, retention policy, access review, and audit evidence outside this repository.

## PCI DSS 4.0 Alignment

### Requirement 3: Protect Stored Account Data

Production storage encryption should be handled at the infrastructure layer. PostgreSQL should run on encrypted managed storage such as Amazon RDS with KMS-managed encryption for storage, snapshots, backups, and replicas. The current codebase does not claim application-layer field encryption.

Implemented application controls reduce sensitive-data exposure:

- `PaymentRequest` and downstream payloads validate payment fields before Kafka ingestion.
- `fraud-engine-configs/logback-spring.xml` masks account IDs, card-like values, emails, and long exception traces before stdout emission.
- Vault-backed configuration keeps database, Redis, Gemini, JWT, and Kafka SASL credentials out of Java source and service YAML literals.
- The AI agent stores decision evidence and bounded reasoning in `fraud_agent.fraud_decisions`; production retention should minimize how long this evidence is kept.

Production additions required for PCI operation:

- RDS KMS CMK with rotation and separation of duties.
- TLS for database and Kafka connections.
- IAM or Vault-issued short-lived database credentials.
- Centralized encrypted log retention with restricted access.
- Tokenized or surrogate account identifiers if real PAN or bank-account numbers enter the platform.

### Requirement 10: Log And Monitor Access

The ledger service records balance changes in immutable audit rows:

- `ledger.audit_logs` captures `payment_id`, `account_id`, `action`, `actor`, timestamps, and balance before/after values.
- `trg_accounts_audit_balance_update` inserts audit rows after account balance changes.
- `trg_audit_logs_immutable_update` and `trg_audit_logs_immutable_delete` reject audit-row mutation.
- `AuditContext` binds payment ID and actor context to the current PostgreSQL transaction.

For production evidence, audit rows should also be exported or replicated to storage with write-once retention, such as S3 Object Lock or a SIEM evidence store. Database-trigger immutability protects application behavior, but it is not a replacement for infrastructure-level retention lock.

## SOC 2 Type II Alignment

### Security

The system uses layered controls:

- Spring Security WebFlux requires JWT authentication for `/api/v1/payments/**` and denies unmatched Gateway routes.
- The SSE stream and health/info endpoints are explicitly permitted while payment ingestion remains protected.
- Redis rate limiting reduces abuse of the payment route and hashes API keys before they become Redis keys.
- Kafka consumers validate schema version, event type, correlation ID, payload constraints, and payment ID key alignment.
- Kafka SASL configuration is available through the `kafka-sasl` profile and resolves credentials from Vault/environment placeholders.

### Confidentiality

Secret handling is centralized:

- Config Server can use a Vault profile with KV v2 backend `secret/fraud-engine`.
- Services consume dynamic placeholders rather than hardcoded secrets.
- Vault bootstrap writes database, Redis, Gemini, JWT, and Kafka SASL values for local development.
- Production should replace dev-mode Vault and root tokens with TLS, workload identity, scoped policies, audit devices, and rotation.

Confidentiality controls for logs and AI processing:

- Logback redaction runs before stdout emission.
- Gemini receives bounded transaction context and similar-record summaries, not unrestricted customer profiles.
- The deterministic Java rules engine remains the decision authority.

## GDPR And India's DPDP Act Alignment

The implementation applies data minimization and purpose limitation to payment integrity processing:

- Gateway accepts only the fields needed for fraud evaluation and ledger posting.
- PII-like values are redacted from logs before centralized observability ingestion.
- Secrets and credentials are isolated in Vault rather than committed files.
- External model usage is limited to explanation generation from bounded evidence.
- If account IDs represent personal data, production should tokenize or pseudonymize them before Gateway ingestion or prompt construction.

Data-subject rights and retention require operational policy:

- Ledger entries and immutable audit rows may need legal retention for financial integrity and anti-fraud obligations.
- Fraud decision evidence should have explicit retention periods, access controls, and deletion/anonymization workflows where legally allowed.
