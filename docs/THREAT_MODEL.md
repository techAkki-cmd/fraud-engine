# Threat Model: Agentic Payment Integrity & Fraud Engine

## Scope And Architecture Boundary

This threat model covers payment ingestion, fraud evaluation, ledger posting, configuration, and analyst streaming in the current codebase. The main components are Spring Cloud Gateway, Redis rate limiting, Kafka, Spring AI with Gemini, PostgreSQL with pgvector, Vault-backed configuration, and a ledger service backed by PostgreSQL row locks and immutable audit triggers.

The local Docker Compose environment is a production-style simulation. A real deployment still needs cloud network isolation, managed identity, service-to-service mTLS, and managed key custody.

## Trust Boundaries

| Boundary | Entry Point | Primary Assets | Implemented Controls |
|---|---|---|---|
| Browser/client to Gateway | `POST /api/v1/payments`, `GET /api/v1/stream/payments` | Payment payloads, JWT claims, correlation IDs | Spring Security WebFlux, deny-by-default routing, Redis rate limiting, request validation. |
| Gateway to Kafka | `payment-ingest` records | Payment events, event headers, trace context | Reactive Kafka producer, payment ID as key, schema/event/correlation headers. |
| Kafka to AI and Ledger | Kafka consumers | Fraud decisions, ledger mutations | Header validation, key validation, idempotency claims, bounded retries. |
| Services to Config | Config Server and Vault | DB, Redis, Gemini, JWT, and Kafka credentials | Vault KV v2, server-side token auth, no static secrets in Java source. |
| Services to PostgreSQL | JPA/JDBC/Flyway | Accounts, ledger entries, fraud decisions, vectors, audit rows | Schema constraints, Flyway, pessimistic locking, audit immutability triggers. |
| AI Agent to Gemini | Gemini chat model | Transaction context, rules, similar profile summaries | Deterministic decision first, explanation-only prompt, strict JSON parsing, bounded reasoning. |

## STRIDE Analysis

| Category | Threat | Impact | Current Mitigation | Production Follow-Up |
|---|---|---|---|---|
| Spoofing | A client submits forged payment requests directly to the Gateway. | Unauthorized payment events could enter Kafka. | JWT authentication on `/api/v1/payments/**`, explicit anonymous access only for SSE/health/info, externalized issuer/JWK settings. | Enforce short-lived tokens, audience validation, IdP key rotation, and ALB/WAF controls. |
| Tampering | A malformed payload or route bypass attempt reaches payment ingestion. | Bad records, contract drift, or resource exhaustion. | Gateway route rate limiting, internal route check, `PaymentRequest` validation, bounded correlation ID, reactive Kafka publish path. | Add WAF request limits, header normalization, Redis TLS, and mTLS between ingress and services. |
| Repudiation | A service or operator disputes a balance mutation. | Weak investigation trail for financial controls. | PostgreSQL audit trigger records balance changes; audit rows reject update/delete; `AuditContext` binds payment and actor context. | Stream audit rows to immutable storage or SIEM and bind actor identity to workload identity. |
| Information Disclosure | Raw account IDs, card-like values, emails, or secrets leak through logs/config. | Exposure of regulated financial data or credentials. | Central Logback redaction, Vault-backed config, no hardcoded Java secrets. | Encrypt log sinks, restrict access, validate redaction in CI, use managed secret rotation. |
| Tampering | A producer injects Kafka records with mismatched keys or headers. | AI or ledger may process an event not bound to the declared payment ID. | Gateway writes schema/event/correlation headers and uses `paymentId` as key; consumers reject wrong headers, blank values, and key mismatches. | Enforce MSK TLS, SASL/IAM auth, topic ACLs, broker audit logs, and multi-AZ replication. |
| Denial Of Service | Flooding payment ingestion overwhelms Gateway, Kafka, Redis, or consumers. | Increased latency or unavailable ingestion. | Redis token bucket rate limiting, reactive publish timeout behavior, manual Kafka acknowledgement, error handlers. | Add WAF limits, autoscaling, lag alerts, backpressure dashboards, and tenant quotas. |
| Elevation Of Privilege | A process bypasses ledger ordering or mutates balances concurrently. | Double debit, incorrect balances, or inconsistent entries. | `PESSIMISTIC_WRITE` account locks, deterministic account ordering, idempotency claims, concurrent integration tests. | Restrict database roles so only ledger service can mutate balances; audit break-glass access. |
| AI Tampering | Transaction fields try to prompt Gemini into contradicting the rules decision. | Analysts could be misled by explanation text. | Rules engine runs first; prompt is explanation-only; parser accepts strict JSON; fallback reasoning is deterministic. | Add prompt/response telemetry review, model version approval, and DLP checks. |

## Control Summary

The implemented posture is defense in depth:

- Gateway authentication and rate limiting protect ingress.
- Kafka contract validation protects event integrity.
- Vault keeps runtime secrets out of source.
- Logback redaction reduces accidental disclosure.
- Deterministic rules prevent LLM authority drift.
- PostgreSQL row locks protect balance correctness.
- Immutable triggers protect audit evidence.

The main remaining production work is infrastructure hardening: managed identity, network isolation, encryption, retention lock, service mesh policy, and operational evidence collection.
