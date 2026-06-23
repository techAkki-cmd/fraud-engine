# Model Governance: Hybrid Risk Engine

## Governance Position

The Hybrid Risk Engine is not an autonomous LLM payment judge. The deterministic Java `RiskScoringEngine` makes the final `SAFE`, `REVIEW`, or `BLOCK` decision. Gemini is used only after that decision exists, and only to write a short analyst-facing explanation.

No production workflow should treat Gemini output as an approval, denial, override, or ledger-posting signal.

## Decision Pipeline

1. Gateway validates the payment request and publishes a Kafka event keyed by `paymentId`.
2. The AI agent consumes the record and validates schema, headers, correlation ID, and key alignment through `PaymentEventDecoder`.
3. `FraudEvaluationService` calls `RiskScoringEngine.score(payment)` before any model interaction.
4. The rules engine scores deterministic signals such as amount-to-median ratio, recent attempts, geo-distance, destination age, graph flags, device risk, and trusted merchant history.
5. The score is clamped to `0..100` and mapped to `SAFE`, `REVIEW`, or `BLOCK`.
6. The agent retrieves a small set of similar profiles from pgvector.
7. Gemini receives the fixed decision, score, triggered rules, transaction context, and bounded retrieval evidence.
8. Gemini must return strict JSON with one `reasoning` field.
9. The agent stores decision evidence, risk score, rules, reasoning, model identifier, vector matches, and timestamps in `fraud_agent.fraud_decisions`.
10. If pgvector or Gemini fails, the system publishes deterministic fallback reasoning and preserves the rules-engine decision.

## Why The Rules Engine Has Authority

The rules engine is auditable and reproducible. Given the same payload and risk context, it produces the same score, the same triggered rules, and the same routing decision. That makes it possible to explain why an INR payment was cleared, held for review, or blocked without depending on model behavior at replay time.

Examples of deterministic signals:

- High amount-to-median ratio increases risk.
- More than five attempts in ten minutes increases risk.
- Long geo-distance from the usual location increases risk.
- Digital wallet payments to very new destinations increase risk.
- Previously flagged destinations and high device-fingerprint risk increase risk.
- Long-tenured destinations and established low-risk merchants reduce risk.

## LLM Guardrails

Gemini is constrained by runtime controls:

- The system prompt says the rules-engine decision is fixed.
- The prompt asks for explanation only, not a new decision.
- The response parser accepts only strict JSON: `{"reasoning":"short operational reason"}`.
- Unknown fields, malformed JSON, blank responses, and overlong reasoning are rejected.
- Reasoning length is capped by `app.ai.max-reasoning-length`.
- Model calls can be disabled with `app.ai.model-call-enabled=false`.
- Vector or model failures do not block deterministic decision publication.

## Retrieval Governance

pgvector gives Gemini a small evidence packet instead of broad database access. `PgVectorSearchClient` uses top-K similarity search, and `FraudEvaluationService` truncates each retrieved document summary before it reaches the prompt.

That keeps explanations grounded while also recording the vector-match evidence that influenced the analyst note.

## Audit Evidence

The AI decision record captures:

- `payment_id`
- processing status
- deterministic decision
- risk score
- triggered rules
- model-generated or fallback reasoning
- model identifier
- vector match summary
- update timestamp

Ledger correctness is enforced separately by PostgreSQL row locks, idempotency claims, and immutable audit triggers. The fraud record explains risk assessment; the ledger audit log proves balance mutation history.

## Failure And Override Policy

The system fails closed with respect to model authority. Gemini failure, malformed output, vector-search unavailability, or timeout never grants approval. Human analysts may review `REVIEW` outcomes in the dashboard, but a real manual override flow should be a separate controlled workflow with identity, segregation of duties, immutable logging, and reason-code capture.
