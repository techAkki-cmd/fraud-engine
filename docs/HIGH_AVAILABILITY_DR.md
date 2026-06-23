# High Availability And Disaster Recovery Strategy

## Service Objectives

| Objective | Target | Meaning |
|---|---:|---|
| Recovery Time Objective | `< 2 minutes` | Payment ingestion and analyst visibility should recover or fail over within two minutes of a regional or writer incident. |
| Recovery Point Objective | `< 10 seconds` | Confirmed payment, fraud, and ledger data loss should stay below ten seconds through managed replication. |
| Payment Ingestion Availability | Multi-AZ active/active | Gateway, Kafka, Redis, and service tasks should tolerate one Availability Zone failure. |
| Ledger Consistency | Strong writer consistency | Account balance updates stay serialized through the ledger database writer and row locks. |

## AWS Production Topology

| Local Component | Production Shape | Availability Design |
|---|---|---|
| Spring Gateway and services | ECS Fargate or EKS behind ALB and private service mesh | At least three tasks across three AZs, health checks on `/actuator/health`, autoscaling on CPU, p95 latency, and Kafka lag. |
| Redis rate limiter | Amazon ElastiCache for Redis | Primary plus replicas across AZs, TLS/auth enabled, security group scoped to Gateway tasks. |
| Kafka | Amazon MSK provisioned or serverless | Replication factor `3`, minimum ISR `2`, TLS, SASL/SCRAM or IAM auth, ACLs, lag alarms. |
| PostgreSQL/pgvector | Amazon RDS PostgreSQL or Aurora PostgreSQL | Multi-AZ writer failover, KMS encryption, PITR, backups, read replicas for non-ledger reads. |
| Vault dev container | Vault Enterprise or AWS Secrets Manager | TLS, workload identity, automatic rotation, audit devices, KMS unseal where applicable. |
| Config Server | Private ECS/EKS service | Replicated across AZs, no public ingress, reads config and secret backend from private subnets. |
| Next.js dashboard | Vercel or AWS Amplify | Multi-region UI deployment with region-aware Gateway URL. |
| Observability | Managed Prometheus, Grafana, CloudWatch, X-Ray/OpenTelemetry | Encrypted logs, retention controls, SLO burn-rate alerts, trace correlation. |

## Multi-Region Routing

Production can run active/standby or active/active depending on data residency and operational appetite. Route 53 latency or failover routing should send users to a healthy regional ALB. Health checks should include Gateway, Config Server dependency health, MSK broker health, Redis availability, and RDS writer availability.

The dashboard should use a region-aware `NEXT_PUBLIC_GATEWAY_URL` so its SSE stream connects to the same regional Gateway serving payment events.

## Data Replication Strategy

PostgreSQL is the recovery anchor because it owns ledger correctness. Use an RDS Multi-AZ writer for low-RTO failover inside a region. Cross-region recovery can use RDS cross-region replicas or Aurora Global Database if the deployment moves to Aurora PostgreSQL with pgvector compatibility.

Kafka topics should replicate across regions through MSK Replicator or MirrorMaker 2. Preserve payment IDs as keys and keep the headers used by AI and ledger decoders.

The ledger service remains single-writer per account at the database row level. Horizontal scaling is safe because `PESSIMISTIC_WRITE` locks and deterministic account ordering serialize balance changes.

## Database Failover Runbook

1. Detect the incident through RDS events, CloudWatch alarms, failed `/actuator/health`, elevated ledger latency, or connection exhaustion.
2. Confirm whether Multi-AZ failover has already promoted a healthy writer. If promotion is in progress, keep Gateway health visible and monitor Kafka publish and consumer lag.
3. If ledger writes fail or mutation latency breaches SLO, temporarily freeze payment ingestion at ALB/WAF or Gateway routing while keeping read-only health and dashboard endpoints available.
4. Promote the designated replica or trigger managed failover. Confirm the writer endpoint accepts connections from ledger, AI, and Config Server subnets.
5. Verify Flyway state and confirm `ledger.accounts`, `ledger.ledger_entries`, `ledger.idempotency_keys`, and `ledger.audit_logs` exist.
6. Compare latest audit rows, ledger entries, Kafka offsets, and topic high-water marks. The accepted loss window must stay below RPO `< 10 seconds`.
7. Resume ledger consumers first, then AI consumers, then Gateway ingestion. Drain Kafka lag while watching duplicate claims and consumer error rates.
8. Reconcile accepted payments by `payment_id` across Kafka records, `fraud_agent.fraud_decisions`, `ledger.ledger_entries`, and `ledger.idempotency_keys`.
9. Re-enable normal traffic and validate payment ingestion, SSE, Config Server, Vault/Secrets, Redis rate limiting, and dashboards.
10. Preserve incident evidence: RDS events, MSK lag metrics, immutable audit rows, traces, logs, and operator actions.

## Operational Guardrails

Docker Compose is a production-style simulation, not a production boundary. A real deployment needs private subnets, ALB/WAF, service-to-service mTLS, least-privilege security groups, encrypted EBS/RDS/MSK/Redis storage, managed keys, immutable backups, Trivy or equivalent image scanning, and integration-test gates before rollout.
